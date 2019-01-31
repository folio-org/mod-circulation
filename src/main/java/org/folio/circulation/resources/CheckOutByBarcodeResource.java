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
import java.util.UUID;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isLongTermLoans;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.isShortTermLoans;
import static org.folio.circulation.domain.representations.CheckOutByBarcodeRequest.ITEM_BARCODE;
import static org.folio.circulation.support.PeriodUtil.*;
import static org.folio.circulation.support.ValidationErrorFailure.failure;

public class CheckOutByBarcodeResource extends Resource {

  public static final String DATE_TIME_FORMATTER = "yyyy-MM-dd'Z'";
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
      .thenComposeAsync(r -> calendarRepository.lookupLibraryHours(r, checkoutServicePointId))
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

    // if the calendar API (`GET: /calendar/periods/:id/period`) doesn't have LibraryHours
    // then the due date calculated like: `Keep Current DueDate`
    if (Objects.isNull(calendar.getRepresentation())) {
      List<Calendar> openingPeriods = libraryHours.getOpeningPeriods();
      DateTime endDate = new DateTime(openingPeriods.get(0).getEndDate())
        .withZoneRetainFields(DateTimeZone.UTC);
      return calculateNewInitialDueDate(loanAndRelatedRecords, endDate);
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
        return calculateDefaultInitialDueDate(loanAndRelatedRecords, loan, loanPolicy);
    }
  }

  private DateTime getDateTimeForFixedPeriod(OpeningDayPeriod prevDayPeriod) {
    OpeningDay openingDay = prevDayPeriod.getOpeningDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (openingDay.getAllDay()) {
      return dateTimeWrapper(localDate.atTime(LocalTime.MAX));
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
      LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
      return dateTimeWrapper(LocalDateTime.of(localDate, localTime));
    }
  }

  private HttpResult<LoanAndRelatedRecords> calculateShortTermDueDate(LoanAndRelatedRecords loanAndRelatedRecords,
                                                                      Loan loan, LoanPolicy loanPolicy, Calendar calendar,
                                                                      DueDateManagement dueDateManagement) {
    List<OpeningDayPeriod> openingDays = calendar.getOpeningDays();

    switch (dueDateManagement) {
      case MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS:
        LoanPolicyPeriod periodSp = calendar.getPeriod();
        int durationSp = calendar.getDuration();

        DateTime dateTime = getShortTermDueDateEndCurrentHours(openingDays, periodSp, durationSp);
        return calculateNewInitialDueDate(loanAndRelatedRecords, dateTime);

      case MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS:
        LoanPolicyPeriod period = calendar.getPeriod();
        int duration = calendar.getDuration();
        LoanPolicyPeriod offsetInterval = loanPolicy.getOffsetPeriodInterval();
        int offsetDuration = loanPolicy.getOffsetPeriodDuration();

        DateTime dateTimeNextPoint = getShortTermDueDateNextHours(openingDays, period, duration, offsetInterval, offsetDuration);
        return calculateNewInitialDueDate(loanAndRelatedRecords, dateTimeNextPoint);

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
                                                                       DateTime newDueDate) {
    Loan loan = loanAndRelatedRecords.getLoan();
    DateTime dueDateWithZone = newDueDate.withZoneRetainFields(DateTimeZone.UTC);
    loan.changeDueDate(dueDateWithZone);
    return HttpResult.succeeded(loanAndRelatedRecords);
  }

  private DateTime getShortTermDueDateEndCurrentHours(List<OpeningDayPeriod> openingDays,
                                                      LoanPolicyPeriod period, int duration) {

    OpeningDayPeriod prevOpeningPeriod = openingDays.get(POSITION_PREV_DAY);
    OpeningDay currentOpeningDay = openingDays.get(POSITION_CURRENT_DAY).getOpeningDay();

    if (!currentOpeningDay.getOpen()) {
      return getTermDueDate(prevOpeningPeriod);
    }

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    LocalTime timeOfCurrentDay = LocalTime.now(ZoneOffset.UTC);
    LocalTime timeShift = getTimeShift(timeOfCurrentDay, period, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      return getDateTimeInsideOpeningDay(currentOpeningDay, dateOfCurrentDay, timeShift);
    }

    OpeningDay prevOpeningDay = prevOpeningPeriod.getOpeningDay();
    return getDateTimeOutsidePeriod(prevOpeningDay, currentOpeningDay, dateOfCurrentDay, timeShift);
  }

  private DateTime getDateTimeOutsidePeriod(OpeningDay prevOpeningDay, OpeningDay currentOpeningDay,
                                            LocalDate dateOfCurrentDay, LocalTime timeShift) {
    LocalTime[] startAndEndTime = getStartAndEndTime(currentOpeningDay.getOpeningHour());
    LocalTime startTime = startAndEndTime[0];
    LocalTime endTime = startAndEndTime[1];

    if (timeShift.isAfter(endTime)) {
      return dateTimeWrapper(LocalDateTime.of(dateOfCurrentDay, endTime));
    }

    if (timeShift.isBefore(startTime)) {
      LocalDate dateOfPrevDay = LocalDate.parse(prevOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      LocalTime prevEndTime = getStartAndEndTime(prevOpeningDay.getOpeningHour())[1];
      return dateTimeWrapper(LocalDateTime.of(dateOfPrevDay, prevEndTime));
    }

    return dateTimeWrapper(LocalDateTime.of(dateOfCurrentDay, timeShift));
  }

  private DateTime getShortTermDueDateNextHours(List<OpeningDayPeriod> openingDays,
                                                LoanPolicyPeriod period, int duration,
                                                LoanPolicyPeriod offsetInterval, int offsetDuration) {

    OpeningDay prevOpeningDay = openingDays.get(POSITION_PREV_DAY).getOpeningDay();
    OpeningDay currentOpeningDay = openingDays.get(POSITION_CURRENT_DAY).getOpeningDay();
    OpeningDay nextOpeningDay = openingDays.get(POSITION_NEXT_DAY).getOpeningDay();

    if (!currentOpeningDay.getOpen()) {
      return getDateTimeNextOrPrevOpeningDay(prevOpeningDay, nextOpeningDay, offsetInterval, offsetDuration);
    }

    LocalDate dateOfCurrentDay = LocalDate.parse(currentOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    LocalTime timeOfCurrentDay = LocalTime.now(ZoneOffset.UTC);
    LocalTime timeShift = getTimeShift(timeOfCurrentDay, period, duration);

    if (isDateTimeWithDurationInsideDay(currentOpeningDay, timeShift)) {
      return getDateTimeInsideOpeningDay(currentOpeningDay, dateOfCurrentDay, timeShift, offsetInterval, offsetDuration);
    }

    // Exception case when dateTime is outside the period
    LocalTime[] startAndEndTime = getStartAndEndTime(currentOpeningDay.getOpeningHour());
    LocalTime startTime = startAndEndTime[0];
    LocalTime endTime = startAndEndTime[1];

    if (timeShift.isBefore(startTime)) {
      LocalDate dateOfNextDay = LocalDate.parse(nextOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      return getStartDateTimeOfOpeningDay(nextOpeningDay, dateOfNextDay, offsetInterval, offsetDuration);
    }

    if (timeShift.isAfter(endTime)) {
      LocalDate dateOfNextDay = LocalDate.parse(nextOpeningDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      return getStartDateTimeOfOpeningDay(nextOpeningDay, dateOfNextDay, offsetInterval, offsetDuration);
    }

    return dateTimeWrapper(LocalDateTime.of(dateOfCurrentDay, timeShift));
  }

  /**
   * Get `dateTime` of the next day or the previous one if the next day is closed
   * <p>
   * An exceptional scenario is possible when `dateTime` falls on the limit value of the period when the library is closed.
   * In this case, we cannot know the next or current open day and we will be guided to the previous one.
   */
  private DateTime getDateTimeNextOrPrevOpeningDay(OpeningDay prevOpeningDay, OpeningDay nextOpeningDay,
                                                   LoanPolicyPeriod offsetInterval, int offsetDuration) {
    return nextOpeningDay.getOpen()
      ? getDateTimeOfOpeningDay(nextOpeningDay, offsetInterval, offsetDuration)
      : getDateTimeOfOpeningDay(prevOpeningDay, offsetInterval, offsetDuration);
  }

  private DateTime getDateTimeOfOpeningDay(OpeningDay openingDay, LoanPolicyPeriod offsetInterval, int offsetDuration) {
    LocalDate date = LocalDate.parse(openingDay.getDate(), DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    return getStartDateTimeOfOpeningDay(openingDay, date, offsetInterval, offsetDuration);
  }

  /**
   * Get the start date of the period or the beginning of the day for the day where the day is open all day
   */
  private DateTime getStartDateTimeOfOpeningDay(OpeningDay openingDay, LocalDate date,
                                                LoanPolicyPeriod offsetInterval, int offsetDuration) {
    if (openingDay.getAllDay()) {
      return calculateOffset(openingDay, date, LocalTime.MIN, offsetInterval, offsetDuration);
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    LocalTime startTime = LocalTime.parse(openingHoursList.get(0).getStartTime());
    return calculateOffset(openingDay, date, startTime, offsetInterval, offsetDuration);
  }

  /**
   * Get the dateTime inside the period or within the opening day if the day is open all day
   * If `timeShift` is not found then return the start of day or period
   */
  private DateTime getDateTimeInsideOpeningDay(OpeningDay openingDay, LocalDate date,
                                               LocalTime timeShift, LoanPolicyPeriod offsetInterval, int offsetDuration) {
    if (openingDay.getAllDay()) {
      return dateTimeWrapper(LocalDateTime.of(date, timeShift));
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    if (isInPeriodOpeningDay(openingHoursList, timeShift)) {
      return dateTimeWrapper(LocalDateTime.of(date, timeShift));
    }

    LocalTime startTimeOfNextPeriod = findStartTimeOfOpeningPeriod(openingHoursList, timeShift);
    return calculateOffset(openingDay, date, startTimeOfNextPeriod, offsetInterval, offsetDuration);
  }

  /**
   * Get the dateTime inside the period or within the opening day if the day is open all day
   * If `timeShift` is not found then return the end of day or period
   */
  private DateTime getDateTimeInsideOpeningDay(OpeningDay openingDay, LocalDate date,
                                               LocalTime timeShift) {
    if (openingDay.getAllDay()) {
      return dateTimeWrapper(LocalDateTime.of(date, timeShift));
    }

    List<OpeningHour> openingHoursList = openingDay.getOpeningHour();
    if (isInPeriodOpeningDay(openingHoursList, timeShift)) {
      return dateTimeWrapper(LocalDateTime.of(date, timeShift));
    }

    LocalTime endTime = findEndTimeOfOpeningPeriod(openingHoursList, timeShift);
    return dateTimeWrapper(LocalDateTime.of(date, endTime));
  }

  private DateTime calculateOffset(OpeningDay openingDay, LocalDate date, LocalTime time,
                                   LoanPolicyPeriod offsetInterval, int offsetDuration) {

    LocalDateTime dateTime = LocalDateTime.of(date, time);
    List<OpeningHour> openingHours = openingDay.getOpeningHour();
    switch (offsetInterval) {
      case HOURS:
        if (openingDay.getAllDay()) {
          return dateTimeWrapper(dateTime.plusHours(offsetDuration));
        }

        LocalTime offsetTime = time.plusHours(offsetDuration);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      case MINUTES:
        if (openingDay.getAllDay()) {
          return dateTimeWrapper(dateTime.plusMinutes(offsetDuration));
        }

        offsetTime = time.plusMinutes(offsetDuration);
        return getDateTimeOffsetInPeriod(openingHours, date, offsetTime);
      default:
        return dateTimeWrapper(dateTime);
    }
  }

  private DateTime getDateTimeOffsetInPeriod(List<OpeningHour> openingHour, LocalDate date, LocalTime offsetTime) {
    if (isInPeriodOpeningDay(openingHour, offsetTime)) {
      return dateTimeWrapper(LocalDateTime.of(date, offsetTime));
    }

    LocalTime endTimeOfPeriod = findEndTimeOfOpeningPeriod(openingHour, offsetTime);
    return dateTimeWrapper(LocalDateTime.of(date, endTimeOfPeriod));
  }

  /**
   * Find earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  private LocalTime findStartTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return startTimeSecond;
      }
    }
    return time;
  }

  /**
   * Find the time of the end of the period by taking into account the time shift
   */
  private LocalTime findEndTimeOfOpeningPeriod(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod = LocalTime.parse(openingHoursList.get(openingHoursList.size() - 1).getEndTime());
    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }

    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime endTimeFirst = LocalTime.parse(openingHoursList.get(i).getEndTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }
    return LocalTime.parse(openingHoursList.get(0).getEndTime());
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
        LocalTime endTime = LocalTime.parse(openingHour.getEndTime());
        return getDateTimeZoneRetain(LocalDateTime.of(localDate, endTime));
      }
    }
  }

  /**
   * Get DateTime in a specific zone
   */
  private DateTime getDateTimeZoneRetain(LocalDateTime localDateTime) {
    return dateTimeWrapper(localDateTime)
      .withZoneRetainFields(DateTimeZone.UTC);
  }

  private DateTime dateTimeWrapper(LocalDateTime dateTime) {
    return new DateTime(dateTime.toString());
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
