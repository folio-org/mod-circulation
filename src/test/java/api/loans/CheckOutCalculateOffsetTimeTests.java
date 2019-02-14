package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoansPolicyProfile;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Hours;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.END_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.FRIDAY_DATE;
import static api.support.fixtures.CalendarExamples.START_TIME_FIRST_PERIOD;
import static api.support.fixtures.CalendarExamples.getFirstFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getLastFakeOpeningDayByServId;
import static org.folio.circulation.support.PeriodUtil.isInPeriodOpeningDay;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.HOURS_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;

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
  private static final String POLICY_PROFILE_NAME = LoansPolicyProfile.ROLLING.name();
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final String OFFSET_INTERVAL_HOURS = "Hours";
  private static final String OFFSET_INTERVAL_MINUTES = "Minutes";
  private static final int START_VAL = 1;
  private static final String START_OF_A_DAY = "00:00";

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
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, 23);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate =
      getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
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
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate =
      getExpectedDateTimeOfOpeningAllDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
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
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, 4);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate =
      getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_HOURS, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
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
    int duration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    // next day
    OpeningDayPeriod dayPeriod = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate =
      getExpectedDateTimeOfPeriodDay(dayPeriod, OFFSET_INTERVAL_MINUTES, offsetDuration);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration,
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
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, 6);

    // current day
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC)
        .plusHours(offsetDuration);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
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
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    // current day
    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC)
        .plusMinutes(offsetDuration);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_MINUTES, duration,
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
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);
    int offsetDuration = START_VAL;

    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
        .plusHours(offsetDuration);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
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
    LocalTime timeNow = LocalTime.now(DateTimeZone.UTC);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = Hours.hoursBetween(timeNow, endTimeOfPeriod).getHours() + 1;
    } else {
      duration = HOURS_PER_DAY - Hours.hoursBetween(endTimeOfPeriod, timeNow).getHours() + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    LocalDate expectedDate = openingDay.getDate(), DATE_TIME_FORMATTER;
    LocalTime expectedTime = END_TIME_SECOND_PERIOD.plusHours(offsetDuration);

    DateTime expectedDueDate = timeZoneWrapper(expectedDate.toDateTime(expectedTime));
    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
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
    LocalTime timeNow = LocalTime.now(DateTimeZone.UTC);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = Hours.hoursBetween(timeNow, endTimeOfPeriod).getHours() + 1;
    } else {
      duration = HOURS_PER_DAY - Hours.hoursBetween(endTimeOfPeriod, timeNow).getHours() + 1;
    }

    OpeningDay openingDay = getLastFakeOpeningDayByServId(servicePointId).getOpeningDay();
    LocalDate expectedDate = openingDay.getDate(), DATE_TIME_FORMATTER;
    LocalTime expectedTime = START_TIME_FIRST_PERIOD.plusHours(offsetDuration);

    DateTime expectedDueDate = timeZoneWrapper(expectedDate.toDateTime(expectedTime));
    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
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


    DateTime expectedDueDate = FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
      .plusHours(offsetDuration);
    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
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
    int duration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    DateTime expectedDueDate = FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
      .plusMinutes(offsetDuration);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_MINUTES, duration,
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
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    DateTime expectedDueDate =
      FRIDAY_DATE.toDateTime(START_TIME_FIRST_PERIOD, DateTimeZone.UTC)
        .plusMinutes(offsetDuration);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration,
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
  public void testOffsetIntervalMinutesWhenCurrentAndNextDayIsClosed() throws Exception {
    String servicePointId = CASE_PREV_OPEN_AND_CURRENT_NEXT_CLOSED;
    int duration = 1;
    int offsetDuration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    OpeningDayPeriod dayPeriod = getFirstFakeOpeningDayByServId(servicePointId);
    LocalDate expectedDate = dayPeriod.getOpeningDay().getDate();
    LocalTime expectedTime = START_TIME_FIRST_PERIOD.plusMinutes(offsetDuration);
    DateTime expectedDueDate = timeZoneWrapper(expectedDate.toDateTime(expectedTime));

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration,
      OFFSET_INTERVAL_MINUTES, offsetDuration);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(DateTime expectedDueDate, String servicePointId,
                               String interval, int duration,
                               String offsetInterval, int offsetDuration)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry =
      createLoanPolicyOffsetTimeEntry(duration, interval, offsetDuration, offsetInterval);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

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

  private DateTime getDateTimeOffsetInPeriod(List<OpeningHour> openingHour,
                                             LocalDate date, LocalTime offsetTime) {
    if (isInPeriodOpeningDay(openingHour, offsetTime)) {
      return timeZoneWrapper(date.toDateTime(offsetTime));
    }

    LocalTime endTimeOfPeriod = findEndTime(openingHour, offsetTime);
    return timeZoneWrapper(date.toDateTime(endTimeOfPeriod));
  }

  private LocalTime findEndTime(List<OpeningHour> openingHoursList, LocalTime time) {
    LocalTime endTimePeriod =
      openingHoursList.get(openingHoursList.size() - 1).getEndTime();

    if (time.isAfter(endTimePeriod)) {
      return endTimePeriod;
    }

    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = openingHoursList.get(i).getStartTime();
      LocalTime endTimeFirst = openingHoursList.get(i).getEndTime();
      LocalTime startTimeSecond = openingHoursList.get(i + 1).getStartTime();
      if (time.isAfter(startTimeFirst) && time.isBefore(startTimeSecond)) {
        return endTimeFirst;
      }
    }

    return openingHoursList.get(0).getEndTime();
  }

  private DateTime getExpectedDateTimeOfOpeningDay(OpeningDayPeriod openingDayPeriod, LocalTime time,
                                                   String intervalHours, int duration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalTime timeShift = intervalHours.equals(INTERVAL_HOURS)
      ? time.plusHours(duration)
      : time.plusMinutes(duration);

    LocalDate date = openingDay.getDate(), DATE_TIME_FORMATTER;
    return timeZoneWrapper(date.toDateTime(timeShift));
  }

  private DateTime getExpectedDateTimeOfOpeningAllDay(OpeningDayPeriod openingDayPeriod,
                                                      String offsetInterval, int offsetDuration) {
    OpeningDay openingDay = openingDayPeriod.getOpeningDay();
    LocalDate date = openingDay.getDate(), DATE_TIME_FORMATTER;

    LocalTime timeOffset = offsetInterval.equals(INTERVAL_HOURS)
      ? LocalTime.MIDNIGHT.plusHours(offsetDuration)
      : LocalTime.MIDNIGHT.plusMinutes(offsetDuration);

    return timeZoneWrapper(date.toDateTime(timeOffset));
  }

  private DateTime timeZoneWrapper(DateTime dateTime) {
    return new DateTime(dateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
  }

  private String createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyEntry);

    useLoanPolicyAsFallback(loanPolicy.getId());

    return loanPolicy.getId().toString();
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyOffsetTimeEntry(int duration, String intervalId,
                                                     int offsetDuration, String offsetInterval) {
    return new LoanPolicyBuilder()
      .withName(LOAN_POLICY_NAME)
      .withDescription("LoanPolicy")
      .withLoansProfile(POLICY_PROFILE_NAME)
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .withOpeningTimeOffset(Period.from(offsetDuration, offsetInterval))
      .renewFromCurrentDueDate()
      .create();
  }

}
