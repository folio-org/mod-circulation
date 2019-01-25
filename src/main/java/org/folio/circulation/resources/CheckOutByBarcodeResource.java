package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.Calendar;
import org.folio.circulation.domain.CalendarRepository;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.LoanRepresentation;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.RequestQueueRepository;
import org.folio.circulation.domain.UpdateItem;
import org.folio.circulation.domain.UpdateRequestQueue;
import org.folio.circulation.domain.User;
import org.folio.circulation.domain.UserRepository;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.domain.policy.LoanPolicyRepository;
import org.folio.circulation.domain.representations.CheckOutByBarcodeRequest;
import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.domain.validation.AlreadyCheckedOutValidator;
import org.folio.circulation.domain.validation.AwaitingPickupValidator;
import org.folio.circulation.domain.validation.ExistingOpenLoanValidator;
import org.folio.circulation.domain.validation.InactiveUserValidator;
import org.folio.circulation.domain.validation.ItemIsNotLoanableValidator;
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointOfCheckoutPresentValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.PeriodUtil;
import org.folio.circulation.support.RouteRegistration;
import org.folio.circulation.support.WritableHttpResult;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.HOURS;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isLongTermLoans;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isShortTermLoans;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.PeriodUtil.MAX_NANO_VAL;
import static org.folio.circulation.support.PeriodUtil.MAX_SECOND_VAL;
import static org.folio.circulation.support.PeriodUtil.calculateOffset;
import static org.folio.circulation.support.PeriodUtil.calculateOffsetTime;
import static org.folio.circulation.support.PeriodUtil.isInCurrentLocalDateTime;
import static org.folio.circulation.support.PeriodUtil.isOffsetTimeInCurrentDayPeriod;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class CheckOutByBarcodeResource extends Resource {

  public static final String DATE_TIME_FORMATTER = "yyyy-MM-dd'Z'";
  private static final LocalTime START_TIME_OF_DAY = LocalTime.of(6, 0);
  private static final int POSITION_PREV_DAY = 0;
  private static final int POSITION_CURRENT_DAY = 1;
  private static final int POSITION_NEXT_DAY = 2;

  public CheckOutByBarcodeResource(HttpClient client) {
    super(client);
  }

  public void register(Router router) {
    RouteRegistration routeRegistration = new RouteRegistration(
      "/circulation/check-out-by-barcode", router);

    routeRegistration.create(this::checkOut);
  }

  private void checkOut(RoutingContext routingContext) {
    final WebContext context = new WebContext(routingContext);

    JsonObject request = routingContext.getBodyAsJson();

    final JsonObject loan = new JsonObject();
    loan.put("id", UUID.randomUUID().toString());

    copyOrDefaultLoanDate(request, loan);

    final String itemBarcode = request.getString(CheckOutByBarcodeRequest.ITEM_BARCODE);
    final String userBarcode = request.getString(CheckOutByBarcodeRequest.USER_BARCODE);
    final String proxyUserBarcode = request.getString(CheckOutByBarcodeRequest.PROXY_USER_BARCODE);
    final String checkoutServicePointId = request.getString(CheckOutByBarcodeRequest.SERVICE_POINT_ID);

    loan.put(LoanProperties.CHECKOUT_SERVICE_POINT_ID, checkoutServicePointId);

    final Clients clients = Clients.create(context, client);

    final UserRepository userRepository = new UserRepository(clients);
    final ItemRepository itemRepository = new ItemRepository(clients, true, true);
    final RequestQueueRepository requestQueueRepository = RequestQueueRepository.using(clients);
    final LoanRepository loanRepository = new LoanRepository(clients);
    final LoanPolicyRepository loanPolicyRepository = new LoanPolicyRepository(clients);
    final CalendarRepository calendarRepository = new CalendarRepository(clients);

    final ProxyRelationshipValidator proxyRelationshipValidator = new ProxyRelationshipValidator(
      clients, () -> failure(
      "Cannot check out item via proxy when relationship is invalid",
      CheckOutByBarcodeRequest.PROXY_USER_BARCODE,
      proxyUserBarcode));

    final ServicePointOfCheckoutPresentValidator servicePointOfCheckoutPresentValidator
      = new ServicePointOfCheckoutPresentValidator(message -> failure(message,
      CheckOutByBarcodeRequest.SERVICE_POINT_ID, checkoutServicePointId));

    final AwaitingPickupValidator awaitingPickupValidator = new AwaitingPickupValidator(
      message -> failure(message,
        CheckOutByBarcodeRequest.USER_BARCODE, userBarcode));

    final AlreadyCheckedOutValidator alreadyCheckedOutValidator = new AlreadyCheckedOutValidator(
      message -> failure(message, ITEM_BARCODE, itemBarcode));

    final ItemNotFoundValidator itemNotFoundValidator = new ItemNotFoundValidator(
      () -> failure(String.format("No item with barcode %s could be found", itemBarcode),
        ITEM_BARCODE, itemBarcode));

    final InactiveUserValidator inactiveUserValidator = InactiveUserValidator.forUser(userBarcode);
    final InactiveUserValidator inactiveProxyUserValidator = InactiveUserValidator.forProxy(proxyUserBarcode);

    final ExistingOpenLoanValidator openLoanValidator = new ExistingOpenLoanValidator(
      loanRepository, message -> failure(message, ITEM_BARCODE, itemBarcode));

    final ItemIsNotLoanableValidator itemIsNotLoanableValidator = new ItemIsNotLoanableValidator(
      () -> failure("Item is not loanable", ITEM_BARCODE, itemBarcode));

    final UpdateItem updateItem = new UpdateItem(clients);
    final UpdateRequestQueue requestQueueUpdate = UpdateRequestQueue.using(clients);

    final LoanRepresentation loanRepresentation = new LoanRepresentation();

    completedFuture(HttpResult.succeeded(new LoanAndRelatedRecords(Loan.from(loan))))
      .thenApply(servicePointOfCheckoutPresentValidator::refuseCheckOutWhenServicePointIsNotPresent)
      .thenCombineAsync(userRepository.getUserByBarcode(userBarcode), this::addUser)
      .thenCombineAsync(userRepository.getProxyUserByBarcode(proxyUserBarcode), this::addProxyUser)
      .thenApply(inactiveUserValidator::refuseWhenUserIsInactive)
      .thenApply(inactiveProxyUserValidator::refuseWhenUserIsInactive)
      .thenCombineAsync(itemRepository.fetchByBarcode(itemBarcode), this::addItem)
      .thenApply(itemNotFoundValidator::refuseWhenItemNotFound)
      .thenApply(alreadyCheckedOutValidator::refuseWhenItemIsAlreadyCheckedOut)
      .thenComposeAsync(r -> r.after(proxyRelationshipValidator::refuseWhenInvalid))
      .thenComposeAsync(r -> r.after(openLoanValidator::refuseWhenHasOpenLoan))
      .thenComposeAsync(r -> r.after(requestQueueRepository::get))
      .thenApply(awaitingPickupValidator::refuseWhenUserIsNotAwaitingPickup)
      .thenComposeAsync(r -> r.after(loanPolicyRepository::lookupLoanPolicy))
      .thenApply(itemIsNotLoanableValidator::refuseWhenItemIsNotLoanable)
      .thenApply(r -> r.next(this::calculateDefaultInitialDueDate))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriod))
      .thenApply(r -> r.next(this::applyCLDDM))
      .thenComposeAsync(r -> r.after(calendarRepository::lookupPeriodForFixedDueDateSchedule))
      .thenApply(r -> r.next(this::applyFixedDueDateLimit))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> applyCLDDM(
    LoanAndRelatedRecords loanAndRelatedRecords) {
    final LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    final Calendar calendar = loanAndRelatedRecords.getCalendar();
    final DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();

    // if the calendar API (`GET: /calendar/periods/:id/calculateopening`) is not available
    // then the due date calculated like: `Keep Current DueDate`
    if (Objects.isNull(calendar.getRepresentation())) {
      return HttpResult.succeeded(loanAndRelatedRecords);
    }

    if (isKeepCurrentDueDate(dueDateManagement)) {
      return HttpResult.succeeded(loanAndRelatedRecords);
    }

    LoanPolicyPeriod periodInterval = loanPolicy.getPeriodInterval();
    if (isLongTermLoans(periodInterval)) {
      return calculateLongTermDueDate(loanAndRelatedRecords,
        calendar, dueDateManagement);
    } else if (isShortTermLoans(periodInterval)) {
      return calculateShortTermDueDate(loanAndRelatedRecords, loanPolicy,
        calendar, dueDateManagement);
    } else {
      return calculateFixedDueDate(loanAndRelatedRecords,
        calendar, dueDateManagement);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateFixedDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                  Calendar calendar,
                                                                  DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        OpeningDayPeriod prevDayPeriod = openingDays.get(POSITION_PREV_DAY);
        DateTime prevDateTime = getDateTimeForFixedPeriod(prevDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, prevDateTime);

      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        OpeningDayPeriod nextDayPeriod = openingDays.get(POSITION_NEXT_DAY);
        DateTime nextDateTime = getDateTimeForFixedPeriod(nextDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, nextDateTime);

      case MOVE_TO_THE_END_OF_THE_CURRENT_DAY:
        OpeningDayPeriod currentDayPeriod = openingDays.get(POSITION_CURRENT_DAY);
        DateTime currentDateTime = getDateTimeForFixedPeriod(currentDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, currentDateTime);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords);
    }
  }

  private DateTime getDateTimeForFixedPeriod(OpeningDayPeriod prevDayPeriod) {
    OpeningDay openingDay = prevDayPeriod.getOpeningDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (openingDay.getAllDay()) {
      return new DateTime(localDate.atTime(LocalTime.MAX).toString());
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
      LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
      return new DateTime(LocalDateTime.of(localDate, localTime).toString());
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateShortTermDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                      LoanPolicy loanPolicy, Calendar calendar,
                                                                      DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        OpeningDayPeriod openingDayPeriod = openingDays.get(openingDays.size() / 2);

        DateTime dateTime = getTermDueDate(openingDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, dateTime);

      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        LoanPolicyPeriod period = loanPolicy.getPeriodInterval();
        int duration = loanPolicy.getPeriodDuration();
        LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
        int offsetDuration = loanPolicy.getOffsetPeriodDuration();

        DateTime dateTimeNextPoint = getShortTermDueDateRollover(openingDays, period, duration, offsetInterval, offsetDuration);
        return calculateNewInitialDueDate(loanAndRelatedRecords, dateTimeNextPoint);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateLongTermDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                     Calendar calendar, DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        OpeningDayPeriod prevDayPeriod = findOpeningDay(openingDays, POSITION_PREV_DAY);
        DateTime prevDateTime = getTermDueDate(prevDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, prevDateTime);

      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        OpeningDayPeriod nextDayPeriod = findOpeningDay(openingDays, POSITION_NEXT_DAY);
        DateTime nextDateTime = getTermDueDate(nextDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, nextDateTime);

      case MOVE_TO_THE_END_OF_THE_CURRENT_DAY:
        OpeningDayPeriod currentDayPeriod = findOpeningDay(openingDays, POSITION_CURRENT_DAY);
        DateTime currentDateTime = getTermDueDate(currentDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, currentDateTime);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateDefaultInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords) {
    Loan loan = loanAndRelatedRecords.getLoan();
    LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(dueDate);
        return loanAndRelatedRecords;
      });
  }

  private HttpResult<LoanAndRelatedRecords> calculateNewInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                       DateTime newDueDate) {
    Loan loan = loanAndRelatedRecords.getLoan();
    DateTime dueDateWithZone = newDueDate.withZoneRetainFields(DateTimeZone.UTC);
    loan.changeDueDate(dueDateWithZone);
    return HttpResult.succeeded(loanAndRelatedRecords);
  }

  private DateTime getShortTermDueDateRollover(List<OpeningDayPeriod> openingDays,
                                               LoanPolicyPeriod period, int duration,
                                               LoanPolicyPeriod offsetInterval, int offsetDuration) {

    OpeningDayPeriod currentDayPeriod = openingDays.get(openingDays.size() / 2);
    OpeningDayPeriod nextDayPeriod = openingDays.get(openingDays.size() - 1);

    if (period == HOURS) {
      return getRolloverForHourlyPeriod(duration, currentDayPeriod, nextDayPeriod, offsetInterval, offsetDuration);
    } else {
      OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
      String currentDate = currentOpeningDay.getDate();

      if (currentOpeningDay.getOpen()) {
        return getRolloverForMinutesPeriod(duration, currentDayPeriod, nextDayPeriod, currentOpeningDay,
          currentDate, offsetInterval, offsetDuration);
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        String nextDate = nextOpeningDay.getDate();
        LocalDate nextLocalDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

        if (nextOpeningDay.getAllDay()) {
          LocalDateTime localDateTime = nextLocalDate.atTime(LocalTime.MIN);
          return calculateOffset(localDateTime, offsetInterval, offsetDuration);
        }

        OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
        LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
        LocalDateTime localDateTime = LocalDateTime.of(nextLocalDate, startTime);
        return calculateOffset(localDateTime, offsetInterval, offsetDuration);
      }
    }
  }

  private DateTime getRolloverForHourlyPeriod(int duration, OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod,
                                              LoanPolicyPeriod offsetInterval, int offsetDuration) {

    OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
    String currentDate = currentOpeningDay.getDate();
    LocalTime localTime = LocalTime.now(ZoneOffset.UTC);
    LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    // The case when the current day is open all day
    if (currentOpeningDay.getOpen() && currentOpeningDay.getAllDay()) {
      LocalDateTime localDateTime = localDate.atTime(localTime)
        .plusHours(duration);

      return calculateOffset(localDateTime, offsetInterval, offsetDuration);
    }

    // The case when the current day is open and has a certain time period
    if (currentOpeningDay.getOpen()) {
      LocalTime localTimeShift = localTime.plusHours(duration);

      if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, localTimeShift)) {
        List<OpeningHour> openingHoursList = currentDayPeriod.getOpeningDay().getOpeningHour();
        LocalTime startTimeNextAvailablePeriod = findStartTimeOfNextPeriod(openingHoursList, localTimeShift);
        LocalTime offsetTime = calculateOffsetTime(startTimeNextAvailablePeriod, offsetInterval, offsetDuration);
        return new DateTime(LocalDateTime.of(localDate, offsetTime).toString());
      } else {
        return getDateTimeOfNextPeriod(currentDayPeriod, nextDayPeriod, offsetInterval, offsetDuration);
      }
    } else {
      return getDateTimeOfNextPeriod(currentDayPeriod, nextDayPeriod, offsetInterval, offsetDuration);
    }
  }

  /**
   * Find earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  private LocalTime findStartTimeOfNextPeriod(List<OpeningHour> openingHoursList, LocalTime localTime) {
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (localTime.isAfter(startTimeFirst) && localTime.isBefore(startTimeSecond)) {
        return startTimeSecond;
      }
    }
    return localTime;
  }

  private DateTime getDateTimeOfNextPeriod(OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod,
                                           LoanPolicyPeriod offsetInterval, int offsetDuration) {
    OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
    String nextDate = nextOpeningDay.getDate();
    LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (nextOpeningDay.getAllDay()) {
      LocalTime offsetTime = calculateOffsetTime(LocalTime.MIN, offsetInterval, offsetDuration);
      return new DateTime(localDate.atTime(offsetTime).toString());
    } else {
      List<OpeningHour> openingHoursList = nextOpeningDay.getOpeningHour();
      if (openingHoursList.size() == 1) {
        OpeningHour openingHour = openingHoursList.get(0);
        LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
        LocalTime offsetTime = calculateOffsetTime(startTime, offsetInterval, offsetDuration);
        return new DateTime(LocalDateTime.of(localDate, offsetTime).toString());
      }

      // rollover case
      if (isRolloverHours(currentDayPeriod, nextDayPeriod)) {
        String time = nextOpeningDay.getOpeningHour().get(POSITION_CURRENT_DAY).getStartTime();
        LocalTime startTime = LocalTime.parse(time);
        return new DateTime(LocalDateTime.of(localDate, startTime).toString());
      } else {
        LocalTime startTime = openingHoursList.stream()
          .filter(this::isLater)
          .map(period -> LocalTime.parse(period.getStartTime()))
          .findAny()
          .orElse(LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getStartTime()));
        return new DateTime(LocalDateTime.of(localDate, startTime).toString());
      }
    }
  }

  /**
   * Determine the rollover hours of service point
   * <p>
   * 'rollover' scenarios where the service point remains open for a continuity of hours that flow from one system date into the next
   */
  private boolean isRolloverHours(OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod) {
    OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
    OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();

    if (!currentOpeningDay.getOpen()) {
      return false;
    }

    // The case when the library works on different days
    LocalDate currentDate = LocalDate.parse(currentOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    LocalDate nextDate = LocalDate.parse(nextOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    if (ChronoUnit.DAYS.between(currentDate, nextDate) > 1) {
      return false;
    }

    // The case when the library is open 24 hours every day
    if (currentOpeningDay.getAllDay() && nextOpeningDay.getAllDay()) {
      return false;
    }

    List<OpeningHour> currentOpeningHours = currentOpeningDay.getOpeningHour();
    LocalTime endTime = LocalTime.parse(currentOpeningHours.get(currentOpeningHours.size() - 1).getEndTime())
      .withSecond(MAX_SECOND_VAL)
      .withNano(MAX_NANO_VAL);

    List<OpeningHour> nextOpeningHours = nextOpeningDay.getOpeningHour();
    LocalTime startTime = LocalTime.parse(nextOpeningHours.get(0).getStartTime());

    return endTime.equals(LocalTime.MAX) && startTime.equals(LocalTime.MIN) && nextOpeningHours.size() > 1;
  }

  private boolean isLater(OpeningHour period) {
    LocalTime startTime = LocalTime.parse(period.getStartTime());
    LocalTime endTime = LocalTime.parse(period.getEndTime());
    return START_TIME_OF_DAY.isBefore(startTime) || START_TIME_OF_DAY.isBefore(endTime);
  }

  private DateTime getRolloverForMinutesPeriod(int duration, OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod,
                                               OpeningDay currentOpeningDay, String currentDate,
                                               LoanPolicyPeriod offsetInterval, int offsetDuration) {

    if (currentOpeningDay.getAllDay()) {
      LocalDate currentLocalDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      LocalDateTime currentEndLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.MAX);
      LocalDateTime offsetLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.now(ZoneOffset.UTC)).plusMinutes(duration);

      if (isInCurrentLocalDateTime(currentEndLocalDateTime, offsetLocalDateTime)) {
        return calculateOffset(offsetLocalDateTime, offsetInterval, offsetDuration);
      } else {
        return getDayForMinutesPeriod(nextDayPeriod, offsetInterval, offsetDuration);
      }
    } else {
      LocalTime offsetTime = LocalTime.now(ZoneOffset.UTC).plusMinutes(duration);
      if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
        LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
        return calculateOffset(LocalDateTime.of(localDate, offsetTime), offsetInterval, offsetDuration);
      } else {
        return getDayForMinutesPeriod(nextDayPeriod, offsetInterval, offsetDuration);
      }
    }
  }

  private DateTime getDayForMinutesPeriod(OpeningDayPeriod nextDayPeriod,
                                          LoanPolicyPeriod offsetInterval, int offsetDuration) {
    OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
    String nextDate = nextOpeningDay.getDate();
    LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (nextOpeningDay.getAllDay()) {
      return calculateOffset(localDate.atTime(LocalTime.MIN), offsetInterval, offsetDuration);
    } else {
      OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
      LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
      return calculateOffset(LocalDateTime.of(localDate, startTime), offsetInterval, offsetDuration);
    }
  }

  private OpeningDayPeriod findOpeningDay(List<OpeningDayPeriod> openingDays, int position) {
    switch (position) {
      case POSITION_PREV_DAY:
        return openingDays.get(position);
      case POSITION_CURRENT_DAY:
        return openingDays.get(position);
      case POSITION_NEXT_DAY:
        return openingDays.get(position);
      default:
        return openingDays.get(POSITION_CURRENT_DAY);
    }
  }

  private DateTime getTermDueDate(OpeningDayPeriod openingDayPeriod) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();

    LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    if (allDay) {
      return getDateTimeZoneRetain(localDate.atTime(LocalTime.MAX));
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      if (openingHours.isEmpty()) {
        return getDateTimeZoneRetain(localDate.atTime(LocalTime.MAX));
      } else {
        OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
        LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
        return getDateTimeZoneRetain(LocalDateTime.of(localDate, localTime));
      }
    }
  }


  /**
   * Get DateTime in a specific zone
   */
  private DateTime getDateTimeZoneRetain(LocalDateTime localDateTime) {
    return new DateTime(localDateTime.toString())
      .withZoneRetainFields(DateTimeZone.UTC);
  }

  /**
   * If CurrentDueDate == KEEP_THE_CURRENT_DUE_DATE or KEEP_THE_CURRENT_DUE_DATE_TIME then the due date
   * should remain unchanged from system calculated due date timestamp
   */
  private boolean isKeepCurrentDueDate(DueDateManagement dueDateManagement) {
    return dueDateManagement == DueDateManagement.KEEP_THE_CURRENT_DUE_DATE
      || dueDateManagement == DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME;
  }

  private HttpResult<LoanAndRelatedRecords> applyFixedDueDateLimit(LoanAndRelatedRecords relatedRecords) {
    final Loan loan = relatedRecords.getLoan();
    final LoanPolicy loanPolicy = relatedRecords.getLoanPolicy();
    Optional<DateTime> optionalDueDateLimit = loanPolicy.getFixedDueDateSchedules()
      .findDueDateFor(loan.getLoanDate());
    if (!optionalDueDateLimit.isPresent()) {
      return HttpResult.succeeded(relatedRecords);
    }
    DateTime dueDateLimit = optionalDueDateLimit.get();

    if (!PeriodUtil.isAfterDate(loan.getDueDate(), dueDateLimit)) {
      return HttpResult.succeeded(relatedRecords);
    }

    LoanPolicyPeriod periodInterval = loanPolicy.getPeriodInterval();

    if (isShortTermLoans(periodInterval)) {
      return recalculateShortTermDueDate(relatedRecords);
    }
    //the same case for long term and fixed loan policy
    return recalculateLongTermDueDate(relatedRecords);
  }

  private HttpResult<LoanAndRelatedRecords> recalculateLongTermDueDate(
    LoanAndRelatedRecords relatedRecords) {
    //TODO: move applying CLDDM to separeate place for reusing
    //Applying 'MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY'
    List<OpeningDayPeriod> openingDays =
      relatedRecords.getFixedDueDateDays().getOpeningDays();
    OpeningDayPeriod prevDayPeriod = findOpeningDay(openingDays, POSITION_PREV_DAY);
    DateTime prevDateTime = getTermDueDate(prevDayPeriod);
    return calculateNewInitialDueDate(relatedRecords, prevDateTime);
  }

  private HttpResult<LoanAndRelatedRecords> recalculateShortTermDueDate(
    LoanAndRelatedRecords relatedRecords) {
    //TODO: move applying CLDDM to separeate place for reusing
    //Applying 'MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS'
    List<OpeningDayPeriod> openingDays =
      relatedRecords.getFixedDueDateDays().getOpeningDays();
    OpeningDayPeriod openingDayPeriod = openingDays.get(openingDays.size() / 2);
    DateTime dateTime = getTermDueDate(openingDayPeriod);
    return calculateNewInitialDueDate(relatedRecords, dateTime);
  }


  private void copyOrDefaultLoanDate(JsonObject request, JsonObject loan) {
    final String loanDateProperty = "loanDate";

    if (request.containsKey(loanDateProperty)) {
      loan.put(loanDateProperty, request.getString(loanDateProperty));
    } else {
      loan.put(loanDateProperty, DateTime.now().toDateTime(DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));
    }
  }

  private WritableHttpResult<JsonObject> createdLoanFrom(HttpResult<JsonObject> result) {
    if (result.failed()) {
      return HttpResult.failed(result.cause());
    } else {
      return new CreatedJsonHttpResult(result.value(),
        String.format("/circulation/loans/%s", result.value().getString("id")));
    }
  }

  private HttpResult<LoanAndRelatedRecords> addProxyUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withProxyingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addUser(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<User> getUserResult) {

    return HttpResult.combine(loanResult, getUserResult,
      LoanAndRelatedRecords::withRequestingUser);
  }

  private HttpResult<LoanAndRelatedRecords> addItem(
    HttpResult<LoanAndRelatedRecords> loanResult,
    HttpResult<Item> inventoryRecordsResult) {

    return HttpResult.combine(loanResult, inventoryRecordsResult,
      LoanAndRelatedRecords::withItem);
  }
}
