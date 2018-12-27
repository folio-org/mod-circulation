package org.folio.circulation.resources;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.folio.circulation.domain.*;
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
import org.folio.circulation.domain.validation.ItemNotFoundValidator;
import org.folio.circulation.domain.validation.ProxyRelationshipValidator;
import org.folio.circulation.domain.validation.ServicePointOfCheckoutPresentValidator;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CreatedJsonHttpResult;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ItemRepository;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.*;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.PeriodUtil.*;
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
    final CalendarRepository libraryHoursRepository = new CalendarRepository(clients);

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
      .thenComposeAsync(r -> libraryHoursRepository.lookupLibraryHours(r, checkoutServicePointId))
      .thenComposeAsync(r -> calendarRepository.lookupPeriod(r, checkoutServicePointId))
      .thenApply(r -> r.next(this::calculateDueDate))
      .thenComposeAsync(r -> r.after(requestQueueUpdate::onCheckOut))
      .thenComposeAsync(r -> r.after(updateItem::onCheckOut))
      .thenComposeAsync(r -> r.after(loanRepository::createLoan))
      .thenApply(r -> r.map(LoanAndRelatedRecords::getLoan))
      .thenApply(r -> r.map(loanRepresentation::extendedLoan))
      .thenApply(this::createdLoanFrom)
      .thenAccept(result -> result.writeTo(routingContext.response()));
  }

  private HttpResult<LoanAndRelatedRecords> calculateDueDate(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    final Loan loan = loanAndRelatedRecords.getLoan();
    final LoanPolicy loanPolicy = loanAndRelatedRecords.getLoanPolicy();
    final Calendar calendar = loanAndRelatedRecords.getCalendar();
    final DueDateManagement dueDateManagement = loanPolicy.getDueDateManagement();
    final LibraryHours libraryHours = loanAndRelatedRecords.getLibraryHours();

    // if the calendar API (`GET: /calendar/periods/:id/calculateopening`) is not available
    // then the due date calculated like: `Keep Current DueDate`
    if (Objects.isNull(calendar.getRepresentation()) && libraryHours.getTotalRecords() == 0) {
      return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }

    if (Objects.isNull(calendar.getRepresentation())) {
      List<Calendar> openingPeriods = libraryHours.getOpeningPeriods();
      if (openingPeriods.isEmpty()) {
        // if the calendar API (`GET: /calendar/periods/:id/period`) doesn't have LibraryHours
        // then the due date calculated like: `Keep Current DueDate`
        return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
      }

      DateTime endDate = new DateTime(openingPeriods.get(0).getEndDate());
      return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, endDate);
    }

    // if loanPolicy is not loanable
    // then the due date calculated like: `Keep Current DueDate`
    if (!loanPolicy.isLoanable()) {
      return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }

    if (isKeepCurrentDueDate(dueDateManagement)) {
      return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }

    LoanPolicyPeriod periodInterval = loanPolicy.getPeriodInterval();
    if (isLongTermLoans(periodInterval)) {
      return calculateLongTermDueDate(loanAndRelatedRecords, loan, loanPolicy,
        calendar, dueDateManagement);
    } else if (isShortTermLoans(periodInterval)) {
      return calculateShortTermDueDate(loanAndRelatedRecords, loan, loanPolicy,
        calendar, dueDateManagement);
    } else {
      return calculateFixedDueDate(loanAndRelatedRecords, loan, loanPolicy,
        calendar, dueDateManagement);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateFixedDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                  Loan loan, LoanPolicy loanPolicy, Calendar calendar,
                                                                  DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        OpeningDayPeriod prevDayPeriod = openingDays.get(POSITION_PREV_DAY);
        DateTime prevDateTime = getDateTimeForFixedPeriod(prevDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, prevDateTime);

      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        OpeningDayPeriod nextDayPeriod = openingDays.get(POSITION_NEXT_DAY);
        DateTime nextDateTime = getDateTimeForFixedPeriod(nextDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, nextDateTime);

      case MOVE_TO_THE_END_OF_THE_CURRENT_DAY:
        OpeningDayPeriod currentDayPeriod = openingDays.get(POSITION_CURRENT_DAY);
        DateTime currentDateTime = getDateTimeForFixedPeriod(currentDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, currentDateTime);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
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
                                                                      Loan loan, LoanPolicy loanPolicy, Calendar calendar,
                                                                      DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        OpeningDayPeriod openingDayPeriod = openingDays.get(openingDays.size() / 2);

        DateTime dateTime = getTermDueDate(openingDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, dateTime);

      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        LoanPolicyPeriod period = calendar.getPeriod();
        int duration = calendar.getDuration();
        LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
        int offsetDuration = loanPolicy.getOffsetPeriodDuration();

        DateTime dateTimeNextPoint = getShortTermDueDateRollover(openingDays, period, duration, offsetInterval, offsetDuration);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, dateTimeNextPoint);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateLongTermDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                     Loan loan, LoanPolicy loanPolicy,
                                                                     Calendar calendar, DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY:
        OpeningDayPeriod prevDayPeriod = findOpeningDay(openingDays, POSITION_PREV_DAY);
        DateTime prevDateTime = getTermDueDate(prevDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, prevDateTime);

      case MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY:
        OpeningDayPeriod nextDayPeriod = findOpeningDay(openingDays, POSITION_NEXT_DAY);
        DateTime nextDateTime = getTermDueDate(nextDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, nextDateTime);

      case MOVE_TO_THE_END_OF_THE_CURRENT_DAY:
        OpeningDayPeriod currentDayPeriod = findOpeningDay(openingDays, POSITION_CURRENT_DAY);
        DateTime currentDateTime = getTermDueDate(currentDayPeriod);
        return calculateNewInitialDueDate(loanAndRelatedRecords, loan, loanPolicy, currentDateTime);

      default:
        return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateDefaultInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                           Loan loan, LoanPolicy loanPolicy) {
    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(dueDate);
        return loanAndRelatedRecords;
      });
  }

  private HttpResult<LoanAndRelatedRecords> calculateNewInitialDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                       Loan loan, LoanPolicy loanPolicy,
                                                                       DateTime newDueDate) {
    return loanPolicy.calculateInitialDueDate(loan)
      .map(dueDate -> {
        loanAndRelatedRecords.getLoan().changeDueDate(newDueDate);
        return loanAndRelatedRecords;
      });
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

    if (currentDayPeriod.getOpeningDay().getAllDay()) {
      String currentDate = currentDayPeriod.getOpeningDay().getDate();
      LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      LocalDateTime localDateTime = localDate.atTime(LocalTime.now(ZoneOffset.UTC)).plusHours(duration);

      return calculateOffset(localDateTime, offsetInterval, offsetDuration);
    } else {
      LocalTime offsetTime = calculateOffsetTime(LocalTime.now(ZoneOffset.UTC).plusHours(duration), offsetInterval, offsetDuration);
      String currentDate = currentDayPeriod.getOpeningDay().getDate();

      if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
        List<OpeningHour> openingHoursList = currentDayPeriod.getOpeningDay().getOpeningHour();
        Optional<LocalTime> startTimeOpt = openingHoursList.stream()
          .filter(period -> isTimeInHourPeriod(period, offsetTime))
          .map(period -> LocalTime.parse(period.getStartTime()))
          .findAny();

        if (startTimeOpt.isPresent()) {
          LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
          return new DateTime(LocalDateTime.of(localDate, startTimeOpt.get()).toString());
        }

        LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
        LocalTime newOffsetTime = findTimeBetweenPeriods(openingHoursList, offsetTime);
        return new DateTime(LocalDateTime.of(localDate, newOffsetTime).toString());
      } else {
        return getDayForHourlyPeriod(nextDayPeriod);
      }
    }
  }

  private LocalTime findTimeBetweenPeriods(List<OpeningHour> openingHoursList, LocalTime offsetTime) {
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (offsetTime.isAfter(startTimeFirst) && offsetTime.isBefore(startTimeSecond)) {
        return startTimeSecond;
      }
    }
    return offsetTime;
  }

  private DateTime getDayForHourlyPeriod(OpeningDayPeriod nextDayPeriod) {
    OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
    String nextDate = nextOpeningDay.getDate();
    LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (nextOpeningDay.getAllDay()) {
      return new DateTime(localDate.atTime(LocalTime.MIN).toString());
    } else {
      List<OpeningHour> openingHoursList = nextOpeningDay.getOpeningHour();
      if (openingHoursList.size() == 1) {
        OpeningHour openingHour = openingHoursList.get(0);
        LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
        return new DateTime(LocalDateTime.of(localDate, startTime).toString());
      }

      Optional<LocalTime> startTimeOpt = openingHoursList.stream()
        .filter(period -> isTimeInHourPeriod(period, START_TIME_OF_DAY))
        .map(period -> LocalTime.parse(period.getStartTime()))
        .findAny();
      if (startTimeOpt.isPresent()) {
        LocalTime startTime = startTimeOpt.get();
        return new DateTime(LocalDateTime.of(localDate, startTime).toString());
      }

      LocalTime startTime = openingHoursList.stream()
        .filter(this::isLater)
        .map(period -> LocalTime.parse(period.getStartTime()))
        .findAny()
        .orElse(LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getStartTime()));
      return new DateTime(LocalDateTime.of(localDate, startTime).toString());
    }
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
      return new DateTime(localDate.atTime(LocalTime.MAX).toString());
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      if (openingHours.isEmpty()) {
        return new DateTime(localDate.atTime(LocalTime.MAX).toString());
      } else {
        OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
        LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
        return new DateTime(LocalDateTime.of(localDate, localTime).toString());
      }
    }
  }

  /**
   * If CurrentDueDate == KEEP_THE_CURRENT_DUE_DATE or KEEP_THE_CURRENT_DUE_DATE_TIME then the due date
   * should remain unchanged from system calculated due date timestamp
   */
  private boolean isKeepCurrentDueDate(DueDateManagement dueDateManagement) {
    return dueDateManagement == DueDateManagement.KEEP_THE_CURRENT_DUE_DATE
      || dueDateManagement == DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME;
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
