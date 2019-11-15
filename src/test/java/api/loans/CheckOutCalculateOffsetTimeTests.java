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
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.HOURS_PER_DAY;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.OpeningDay;
import api.support.OpeningDayPeriod;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
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
public class CheckOutCalculateOffsetTimeTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the beginning of the next open service point hours";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final String OFFSET_INTERVAL_HOURS = "Hours";
  private static final String OFFSET_INTERVAL_MINUTES = "Minutes";
  private static final int START_VAL = 1;

  private static final LocalTime TEST_TIME_MORNING = new LocalTime(10, 0);
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
  public void testOffsetIntervalHoursIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = 3;

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
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
  public void testOffsetIntervalMinutesIfCurrentDayIsClosed() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 1;
    int offsetDuration = 3;

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
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
  public void testOffsetIntervalHoursIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 3;
    int offsetDuration = 2;

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
      getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(loanDate, expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_HOURS, offsetDuration);
  }

  private DateTime getExpectedDateTimeOfPeriodDay(OpeningDayPeriod dayPeriod,
                                                  String offsetInterval, int offsetDuration) {
    OpeningDay openingDay = dayPeriod.getOpeningDay();
    LocalTime startTime = openingDay.getOpeningHour().get(0).getStartTime();
    return getExpectedDateTimeOfOpeningDay(dayPeriod, startTime, offsetInterval, offsetDuration);
  }

  /**
   * Loan period: Minutes
   * Offset period: Minutes
   * Current day: closed
   * Next day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testOffsetIntervalMinutesIfCurrentDayIsClosedWithPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 2;
    int offsetDuration = 3;

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
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
  public void testOffsetIntervalHoursIfCurrentAllDayOpen() throws Exception {
    int duration = 1;
    int offsetDuration = 3;

    DateTime loanDate = THURSDAY_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    // current day
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC)
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
  public void testOffsetIntervalMinutesIfCurrentAllDayOpen() throws Exception {
    int duration = 15;
    int offsetDuration = 20;

    DateTime loanDate = THURSDAY_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    // current day
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC)
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
  public void testOffsetIntervalHoursForPeriod() throws Exception {
    int duration = 2;
    int offsetDuration = START_VAL;

    DateTime loanDate = THURSDAY_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
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
  public void testOffsetDurationWithinDayHours() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration;
    int offsetDuration = HOURS_PER_DAY;

    LocalTime endTimeOfPeriod = END_TIME_SECOND_PERIOD;
    LocalTime timeNow = new LocalTime(13, 0);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = Hours.hoursBetween(timeNow, endTimeOfPeriod).getHours() + 1;
    } else {
      duration = HOURS_PER_DAY - Hours.hoursBetween(endTimeOfPeriod, timeNow).getHours() + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    DateTime loanDate = openingDay.getDate()
      .toDateTime(new LocalTime(5, 0), DateTimeZone.UTC);
    LocalDate expectedDate = openingDay.getDate();
    LocalTime expectedTime = END_TIME_SECOND_PERIOD.plusHours(offsetDuration);

    DateTime expectedDueDate = timeZoneWrapper(expectedDate.toDateTime(expectedTime));
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
  public void testOffsetIntervalHoursForPeriodCase1() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration;
    int offsetDuration = START_VAL;

    LocalTime endTimeOfPeriod = END_TIME_SECOND_PERIOD;
    LocalTime timeNow = new LocalTime(13, 0);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = Hours.hoursBetween(timeNow, endTimeOfPeriod).getHours() + 1;
    } else {
      duration = HOURS_PER_DAY - Hours.hoursBetween(endTimeOfPeriod, timeNow).getHours() + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    DateTime loanDate = openingDay.getDate()
      .toDateTime(new LocalTime(5, 0), DateTimeZone.UTC);
    LocalDate expectedDate = openingDay.getDate();
    LocalTime expectedTime = START_TIME_SECOND_PERIOD.plusHours(offsetDuration);

    DateTime expectedDueDate = expectedDate.toDateTime(expectedTime, DateTimeZone.UTC);
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
  public void testOffsetIntervalHoursForPeriodCase2() throws Exception {
    int duration;
    int offsetDuration = 1;

    LocalTime starTmeOfPeriod = START_TIME_FIRST_PERIOD;
    LocalTime timeNow = new LocalTime(11, 0);

    // The value is calculated taking into account the transition to the next period
    if (timeNow.isAfter(starTmeOfPeriod)) {
      duration = HOURS_PER_DAY - Hours.hoursBetween(starTmeOfPeriod, timeNow).getHours() - 1;
    } else {
      duration = Hours.hoursBetween(timeNow, starTmeOfPeriod).getHours() - 1;
    }

    DateTime loanDate = THURSDAY_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
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
  public void testOffsetIntervalMinutesForPeriod() throws Exception {
    int duration = 1;
    int offsetDuration = 1;

    DateTime loanDate = THURSDAY_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
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
  public void testOffsetIntervalMinutesCurrentDay() throws Exception {
    int duration = 1;
    int offsetDuration = 1;

    DateTime loanDate = THURSDAY_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
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

    OpeningDayPeriod dayPeriod = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = dayPeriod.getOpeningDay().getDate()
      .toDateTime(new LocalTime(6, 0), DateTimeZone.UTC);
    LocalDate expectedDate = dayPeriod.getOpeningDay().getDate();
    LocalTime expectedTime = START_TIME_FIRST_PERIOD.plusMinutes(offsetDuration);
    DateTime expectedDueDate = timeZoneWrapper(expectedDate.toDateTime(expectedTime));

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry =
      createLoanPolicyOffsetTimeEntry(duration, INTERVAL_HOURS, offsetDuration, OFFSET_INTERVAL_MINUTES);
    createLoanPolicy(loanPolicyEntry);

    JsonObject response = loansFixture.attemptCheckOutByBarcode(
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
  private void checkOffsetTime(DateTime loanDate, DateTime expectedDueDate, String servicePointId,
                               String interval, int duration,
                               String offsetInterval, int offsetDuration)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry =
      createLoanPolicyOffsetTimeEntry(duration, interval, offsetDuration, offsetInterval);
    IndividualResource loanPolicy = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);

    DateTime actualDueDate = getThresholdDateTime(DateTime.parse(loan.getString("dueDate")));
    DateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);

    assertThat("due date should be " + thresholdDateTime + ", actual due date is "
      + actualDueDate, actualDueDate.isEqual(thresholdDateTime));
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private DateTime getThresholdDateTime(DateTime dateTime) {
    return dateTime.withSecondOfMinute(0).withMillisOfSecond(0);
  }

  private DateTime getExpectedDateTimeOfOpeningDay(OpeningDayPeriod openingDayPeriod, LocalTime time,
                                                   String intervalHours, int duration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalTime timeShift = intervalHours.equals(INTERVAL_HOURS)
      ? time.plusHours(duration)
      : time.plusMinutes(duration);

    LocalDate date = openingDay.getDate();
    return timeZoneWrapper(date.toDateTime(timeShift));
  }

  private DateTime getExpectedDateTimeOfOpeningAllDay(OpeningDayPeriod openingDayPeriod,
                                                      String offsetInterval, int offsetDuration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalDate date = openingDay.getDate();

    LocalTime timeOffset = offsetInterval.equals(INTERVAL_HOURS)
      ? LocalTime.MIDNIGHT.plusHours(offsetDuration)
      : LocalTime.MIDNIGHT.plusMinutes(offsetDuration);

    return timeZoneWrapper(date.toDateTime(timeOffset));
  }

  private DateTime timeZoneWrapper(DateTime dateTime) {
    return new DateTime(dateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
  }

  private IndividualResource createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

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
    int duration,
    String intervalId,
    int offsetDuration,
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

}
