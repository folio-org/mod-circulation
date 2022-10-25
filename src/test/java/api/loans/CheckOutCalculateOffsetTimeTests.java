package api.loans;

import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.END_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.FRIDAY_DATE;
import static api.support.fixtures.CalendarExamples.START_TIME_FIRST_PERIOD;
import static api.support.fixtures.CalendarExamples.START_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.THURSDAY_DATE;
import static api.support.fixtures.CalendarExamples.getFirstFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getLastFakeOpeningDayByServId;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateTimeUtil.isAfterMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isBeforeMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.folio.circulation.support.utils.DateTimeUtil.millisBetween;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

/**
 * Test case for Short-term loans
 * Loanable = Y
 * Loan profile = Rolling
 * Closed Library Due Date Management = Move to the beginning of the next open service point hours
 * <p>
 * Expected result:
 * If SPID-1 is determined to be CLOSED for system-calculated due date and timestamp
 * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
 */
class CheckOutCalculateOffsetTimeTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the beginning of the next open service point hours";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final String OFFSET_INTERVAL_HOURS = "Hours";
  private static final String OFFSET_INTERVAL_MINUTES = "Minutes";
  private static final int START_VAL = 1;
  private static final int HOURS_PER_DAY = 24;

  private static final LocalTime TEST_TIME_MORNING = LocalTime.of(10, 0);
  private static final String TIMETABLE_IS_ABSENT_ERROR_MESSAGE = "Calendar timetable is absent for requested date";

  private final String dueDateManagement =
    DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS.getValue();

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testOffsetIntervalHoursIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    long duration = 1;
    long offsetDuration = 3;

    // next day
    OpeningDay dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime
      .of(CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate =
      getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: closed
   * Next day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testOffsetIntervalMinutesIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    long duration = 1;
    long offsetDuration = 3;

    // next day
    OpeningDay dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime
      .of(CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate =
      getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testOffsetIntervalHoursIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    long duration = 3;
    long offsetDuration = 2;

    // next day
    OpeningDay dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime
      .of(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate =
      getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  private ZonedDateTime getExpectedDateTimeOfPeriodDay(OpeningDay openingDay,
      String offsetInterval, long offsetDuration) {
    LocalTime startTime = openingDay.getOpeningHour().get(0).getStartTime();

    return getExpectedDateTimeOfOpeningDay(openingDay, startTime, offsetInterval, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testOffsetIntervalMinutesIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    long duration = 2;
    long offsetDuration = 3;

    // next day
    OpeningDay dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime
      .of(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate =
      getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_MINUTES, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: open allDay
   * Next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalHoursIfCurrentAllDayOpen() throws Exception {
    long duration = 1;
    long offsetDuration = 3;

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);

    // current day
    ZonedDateTime expectedDueDate = ZonedDateTime
      .of(FRIDAY_DATE, LocalTime.MIDNIGHT, UTC)
      .plusHours(offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: open allDay
   * Next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalMinutesIfCurrentAllDayOpen() throws Exception {
    long duration = 15;
    long offsetDuration = 20;

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);
    // current day
    ZonedDateTime expectedDueDate = ZonedDateTime
      .of(FRIDAY_DATE, LocalTime.MIDNIGHT, UTC)
      .plusMinutes(offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_MINUTES, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalHoursForPeriod() throws Exception {
    long duration = 2;
    long offsetDuration = START_VAL;

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = ZonedDateTime
      .of(FRIDAY_DATE, START_TIME_FIRST_PERIOD, UTC)
      .plusHours(offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetDurationWithinDayHours() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    long duration;
    long offsetDuration = HOURS_PER_DAY;

    LocalTime endTimeOfPeriod = END_TIME_SECOND_PERIOD;
    LocalTime timeNow = LocalTime.of(13, 0);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (isBeforeMillis(timeNow, endTimeOfPeriod)) {
      duration = (int) hoursBetween(timeNow, endTimeOfPeriod) + 1;
    } else {
      duration = HOURS_PER_DAY - (int) hoursBetween(endTimeOfPeriod, timeNow) + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime.of(openingDay.getDate(), LocalTime.of(5, 0), UTC);
    LocalDate expectedDate = openingDay.getDate();
    LocalTime expectedTime = END_TIME_SECOND_PERIOD.plusHours(offsetDuration);

    ZonedDateTime expectedDueDate = ZonedDateTime.of(expectedDate, expectedTime, UTC);
    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalHoursForPeriodCase1() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    long duration;
    long offsetDuration = START_VAL;

    LocalTime endTimeOfPeriod = END_TIME_SECOND_PERIOD;
    LocalTime timeNow = LocalTime.of(13, 0);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (isBeforeMillis(timeNow, endTimeOfPeriod)) {
      duration = hoursBetween(timeNow, endTimeOfPeriod) + 1;
    } else {
      duration = HOURS_PER_DAY - hoursBetween(endTimeOfPeriod, timeNow) + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime.of(openingDay.getDate(), LocalTime.of(5, 0), UTC);
    LocalDate expectedDate = openingDay.getDate();
    LocalTime expectedTime = START_TIME_SECOND_PERIOD.plusHours(offsetDuration);

    ZonedDateTime expectedDueDate = ZonedDateTime.of(expectedDate, expectedTime, UTC);
    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Hours
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalHoursForPeriodCase2() throws Exception {
    long duration;
    long offsetDuration = 1;

    LocalTime starTmeOfPeriod = START_TIME_FIRST_PERIOD;
    LocalTime timeNow = LocalTime.of(11, 0);

    // The value is calculated taking into account the transition to the next period
    if (isAfterMillis(timeNow, starTmeOfPeriod)) {
      duration = HOURS_PER_DAY - hoursBetween(starTmeOfPeriod, timeNow) - 1;
    } else {
      duration = hoursBetween(timeNow, starTmeOfPeriod) - 1;
    }

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = ZonedDateTime.of(FRIDAY_DATE, START_TIME_FIRST_PERIOD, UTC)
      .plusHours(offsetDuration);
    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalMinutesForPeriod() throws Exception {
    long duration = 1;
    long offsetDuration = 1;

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = ZonedDateTime.of(FRIDAY_DATE, START_TIME_FIRST_PERIOD, UTC)
      .plusMinutes(offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_MINUTES, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  void testOffsetIntervalMinutesCurrentDay() throws Exception {
    long duration = 1;
    long offsetDuration = 1;

    ZonedDateTime loanDate = ZonedDateTime.of(THURSDAY_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = ZonedDateTime.of(FRIDAY_DATE, START_TIME_FIRST_PERIOD, UTC)
      .plusMinutes(offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Loan period: Hours
   * Offset period: Minutes
   * Current day: period
   * Next day: period
   * Test period: WED=open, THU=closed, FRI=closed
   */
  @Test
  //TODO change test
  public void testOffsetIntervalMinutesWhenCurrentAndNextDayIsClosed() throws Exception {
    String servicePointId = CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED;
    int duration = 1;
    int offsetDuration = 1;

    OpeningDay dayPeriod = getFirstFakeOpeningDayByServId(servicePointId);
    ZonedDateTime loanDate = ZonedDateTime
      .of(dayPeriod.getDate(), LocalTime.of(6, 0), UTC);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry =
      createLoanPolicyOffsetTimeEntry(duration, INTERVAL_HOURS, offsetDuration, OFFSET_INTERVAL_MINUTES);
    createLoanPolicy(loanPolicyEntry);

    JsonObject response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId)).getJson();

    assertThat(response, hasErrorWith(allOf(
      hasMessage(TIMETABLE_IS_ABSENT_ERROR_MESSAGE))));
  }

  /**
   * Check result
   */
  private void checkOffsetTime(ZonedDateTime loanDate, ZonedDateTime expectedDueDate, String servicePointId,
                               String interval, long duration,
                               String offsetInterval, long offsetDuration)
    throws InterruptedException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry =
      createLoanPolicyOffsetTimeEntry(duration, interval, offsetDuration, offsetInterval);
    IndividualResource loanPolicy = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);
    loanHasOverdueFinePolicyProperties(loan,  overdueFinePoliciesFixture.facultyStandard());
    loanHasLostItemPolicyProperties(loan,  lostItemFeePoliciesFixture.facultyStandard());

    ZonedDateTime actualDueDate = getThresholdDateTime(ZonedDateTime.parse(loan.getString("dueDate")));
    ZonedDateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);

    assertThat("due date should be " + thresholdDateTime + ", actual due date is "
      + actualDueDate, isSameMillis(actualDueDate, thresholdDateTime));
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private ZonedDateTime getThresholdDateTime(ZonedDateTime dateTime) {
    return dateTime.truncatedTo(ChronoUnit.MINUTES);
  }

  private ZonedDateTime getExpectedDateTimeOfOpeningDay(OpeningDay openingDay,
      LocalTime time, String intervalHours, long duration) {
    LocalTime timeShift = intervalHours.equals(INTERVAL_HOURS)
      ? time.plusHours(duration)
      : time.plusMinutes(duration);

    LocalDate date = openingDay.getDate();
    return ZonedDateTime.of(date, timeShift, UTC);
  }

  private ZonedDateTime getExpectedDateTimeOfOpeningAllDay(OpeningDay openingDay,
      String offsetInterval, long offsetDuration) {
    LocalDate date = openingDay.getDate();

    LocalTime timeOffset = offsetInterval.equals(INTERVAL_HOURS)
      ? LocalTime.MIDNIGHT.plusHours(offsetDuration)
      : LocalTime.MIDNIGHT.plusMinutes(offsetDuration);

    return ZonedDateTime.of(date, timeOffset, UTC);
  }

  private IndividualResource createLoanPolicy(JsonObject loanPolicyEntry) {
    IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyEntry);

    UUID requestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    UUID noticePolicyId = noticePoliciesFixture.activeNotice().getId();
    UUID overdueFinePolicyId = overdueFinePoliciesFixture.facultyStandard().getId();
    UUID lostItemFeePolicyId = lostItemFeePoliciesFixture.facultyStandard().getId();
    useFallbackPolicies(loanPolicy.getId(), requestPolicyId, noticePolicyId, overdueFinePolicyId, lostItemFeePolicyId);

    return loanPolicy;
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyOffsetTimeEntry(
    long duration,
    String intervalId,
    long offsetDuration,
    String offsetInterval) {

    return new LoanPolicyBuilder()
      .withName(LOAN_POLICY_NAME)
      .withDescription("LoanPolicy")
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .withOpeningTimeOffset(Period.from(offsetDuration, offsetInterval))
      .renewFromCurrentDueDate()
      .create();
  }

  private long hoursBetween(LocalTime begin, LocalTime end) {
    final LocalDateTime beginDate = LocalDateTime
      .of(LocalDate.EPOCH, begin)
      .truncatedTo(ChronoUnit.HOURS);
    final LocalDateTime endDate = LocalDateTime
      .of(LocalDate.EPOCH, end)
      .truncatedTo(ChronoUnit.HOURS);

    return millisBetween(beginDate, endDate) / 3600000;
  }

}
