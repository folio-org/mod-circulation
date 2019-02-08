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
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.END_TIME_SECOND_PERIOD;
import static api.support.fixtures.CalendarExamples.START_TIME_FIRST_PERIOD;
import static api.support.fixtures.CalendarExamples.WEDNESDAY_DATE;
import static api.support.fixtures.CalendarExamples.getFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getFirstFakeOpeningDayByServId;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategy.DATE_TIME_FORMAT;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategy.DATE_TIME_FORMATTER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.HOURS_PER_DAY;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;

/**
 * Test case for Short-term loans
 * Loanable = Y
 * Loan profile = Rolling
 * Closed Library Due Date Management = Move to the end of the current service point hours
 * <p>
 * Expected result:
 * If SPID-1 is determined to be CLOSED for system-calculated due date and timestamp
 * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
 */
public class CheckOutCalculateDueDateShortTermTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the end of the current service point hours";
  private static final String POLICY_PROFILE_NAME = LoansPolicyProfile.ROLLING.name();
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final int START_VAL = 1;
  private static final String END_OF_DAY = "23:59";

  private final String dueDateManagement =
    DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue();

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: closed
   * Next and prev day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Minutes
   * Current day: closed
   * Next and prev day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);

    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Hours
   * Current and next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanPeriodIfAllDayOpen() throws Exception {
    int duration = new SplittableRandom().nextInt(START_VAL, HOURS_PER_DAY);

    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_OF_DAY);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanPeriodIfDayHasPeriod() throws Exception {
    int duration = 24;

    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_TIME_SECOND_PERIOD);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Minutes
   * Current and next day: open allDay
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMinutesLoanPeriodIfAllDayOpen() throws Exception {
    int duration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_OF_DAY);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Minutes
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMinutesLoanPeriodIfDayHasPeriod() throws Exception {
    int duration = 10;
    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_TIME_SECOND_PERIOD);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_MINUTES, duration);
  }

  /**
   * Loan period: Hours
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanTimeOutsidePeriod() throws Exception {
    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    int duration;

    LocalTime endTimeOfPeriod = LocalTime.parse(END_TIME_SECOND_PERIOD);
    LocalTime timeNow = LocalTime.now(ZoneOffset.UTC);

    // The value of `duration` is calculated taking into account the exit for the period.
    if (timeNow.isBefore(endTimeOfPeriod)) {
      duration = (int) ChronoUnit.HOURS.between(timeNow, endTimeOfPeriod) + 1;
    } else {
      duration = HOURS_PER_DAY - (int) ChronoUnit.HOURS.between(endTimeOfPeriod, timeNow) + 1;
    }

    List<OpeningDayPeriod> openingDayPeriods = getFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_TIME_SECOND_PERIOD);

    checkOffsetTime(expectedDueDate, servicePointId, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current and next day: period
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testHoursLoanTimeOutsidePeriodCase2() throws Exception {
    int duration;

    LocalTime starTmeOfPeriod = LocalTime.parse(START_TIME_FIRST_PERIOD);
    LocalTime timeNow = LocalTime.now(ZoneOffset.UTC);

    // The value is calculated taking into account the transition to the next period
    if (timeNow.isAfter(starTmeOfPeriod)) {
      duration = HOURS_PER_DAY - (int) ChronoUnit.HOURS.between(starTmeOfPeriod, timeNow) - 1;
    } else {
      duration = (int) ChronoUnit.HOURS.between(timeNow, starTmeOfPeriod) - 1;
    }

    DateTime expectedDueDate = buildExpectedDueDate(WEDNESDAY_DATE, END_TIME_SECOND_PERIOD);

    checkOffsetTime(expectedDueDate, CASE_WED_THU_FRI_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(DateTime expectedDueDate, String servicePointId,
                               String interval, int duration)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(duration, interval);
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

    assertThat("due date should be " + thresholdDateTime + ", actual due date is " + actualDueDate,
      actualDueDate.isEqual(thresholdDateTime));
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private DateTime getThresholdDateTime(DateTime dateTime) {
    return dateTime.withSecondOfMinute(0).withMillisOfSecond(0);
  }

  private DateTime getEndDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DATE_TIME_FORMATTER);

    if (allDay) {
      return getDateTimeOfEndDay(localDate);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();
      OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
      LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
      return getDateTimeZoneRetain(LocalDateTime.of(localDate, localTime));
    }
  }

  /**
   * Get the date with the end of the day
   */
  private DateTime getDateTimeOfEndDay(LocalDate localDate) {
    return getDateTimeZoneRetain(localDate.atTime(LocalTime.MAX));
  }


  private DateTime getDateTimeZoneRetain(LocalDateTime localDateTime) {
    return new DateTime(localDateTime.toString())
      .withZoneRetainFields(DateTimeZone.UTC);
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
  private JsonObject createLoanPolicyEntry(int duration, String intervalId) {
    return new LoanPolicyBuilder()
      .withName(LOAN_POLICY_NAME)
      .withDescription("LoanPolicy")
      .withLoansProfile(POLICY_PROFILE_NAME)
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

  private DateTime buildExpectedDueDate(String date, String time) {
    return DateTime.parse(date, DateTimeFormat.forPattern(DATE_TIME_FORMAT))
      .withTime(org.joda.time.LocalTime.parse(time))
      .withZoneRetainFields(DateTimeZone.UTC);
  }

}
