package api.loans;

import static api.support.fixtures.CalendarExamples.CASE_CURRENT_IS_OPEN;
import static api.support.fixtures.CalendarExamples.CASE_CURRENT_IS_OPEN_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_CURRENT_IS_OPEN_PREV_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY;
import static api.support.fixtures.CalendarExamples.END_TIME_FIRST_PERIOD;
import static api.support.fixtures.CalendarExamples.ROLLOVER_SCENARIO_NEXT_DAY_CLOSED_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.ROLLOVER_SCENARIO_SERVICE_POINT_ID;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.fixtures.ConfigurationExample;
import io.vertx.core.json.JsonObject;

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
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final LocalTime TEST_TIME_MORNING = new LocalTime(11, 0);

  private final String dueDateManagement =
    DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue();

  @Test
  public void testRespectSelectedTimezoneForDueDateCalculations() throws Exception {
    String expectedTimeZone = "America/New_York";
    int duration = 24;

    Response response = configClient.create(ConfigurationExample.newYorkTimezoneConfiguration())
      .getResponse();
    assertThat(response.getBody(), containsString(expectedTimeZone));

    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.forID(expectedTimeZone));
    DateTime expectedDueDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.plusDays(1)
      .toDateTime(LocalTime.MIDNIGHT, DateTimeZone.forID(expectedTimeZone));

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  public void testRespectUtcTimezoneForDueDateCalculations() throws Exception {
    int duration = 24;

    Response response = configClient.create(ConfigurationExample.utcTimezoneConfiguration())
      .getResponse();
    assertThat(response.getBody(), containsString(DateTimeZone.UTC.toString()));

    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.plusDays(1)
      .toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  public void testMoveToTheEndOfCurrentServicePointHoursRolloverScenario() throws Exception {
    int duration = 18;

    Response response = configClient.create(ConfigurationExample.utcTimezoneConfiguration())
      .getResponse();
    assertThat(response.getBody(), containsString(DateTimeZone.UTC.toString()));

    DateTime loanDate = CASE_CURRENT_IS_OPEN_CURR_DAY.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);

    DateTime expectedDueDate = CASE_CURRENT_IS_OPEN_CURR_DAY
      .toDateTime(LocalTime.MIDNIGHT.plusHours(3), DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, ROLLOVER_SCENARIO_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  public void testMoveToTheEndOfCurrentServicePointHoursNextDayIsClosed() throws Exception {
    int duration = 1;

    Response response = configClient.create(ConfigurationExample.utcTimezoneConfiguration())
      .getResponse();
    assertThat(response.getBody(), containsString(DateTimeZone.UTC.toString()));

    DateTime loanDate = CASE_CURRENT_IS_OPEN_CURR_DAY.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);

    DateTime expectedDueDate = CASE_CURRENT_IS_OPEN_CURR_DAY
      .toDateTime(LocalTime.MIDNIGHT.minusMinutes(1), DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, ROLLOVER_SCENARIO_NEXT_DAY_CLOSED_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() throws Exception {
    int duration = 24;

    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.plusDays(1)
      .toDateTime(LocalTime.MIDNIGHT, DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  public void testHoursLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY.toDateTime(END_TIME_FIRST_PERIOD, DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_SERVICE_POINT_ID, INTERVAL_HOURS, 25);
  }

  /**
   * Loan period: Minutes
   * Current day: open
   * Next and prev day: period
   */
  @Test
  public void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() throws Exception {
    DateTime loanDate = CASE_CURRENT_IS_OPEN_PREV_DAY
      .toDateTime(END_TIME_FIRST_PERIOD, DateTimeZone.UTC)
      .minusHours(1);
    DateTime expectedDueDate = CASE_CURRENT_IS_OPEN_PREV_DAY
      .toDateTime(END_TIME_FIRST_PERIOD, DateTimeZone.UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_CURRENT_IS_OPEN, INTERVAL_MINUTES, 90);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(DateTime loanDate, DateTime expectedDueDate,
                               String servicePointId, String interval, int duration)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(duration, interval);
    final IndividualResource loanPolicy = createLoanPolicy(loanPolicyEntry);

    DateTimeUtils.setCurrentMillisFixed(loanDate.getMillis());
    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));
    DateTimeUtils.setCurrentMillisSystem();

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);

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

  private IndividualResource createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyEntry);
    UUID requestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    UUID noticePolicyId = noticePoliciesFixture.activeNotice().getId();
    UUID overdueFinePolicyId = overdueFinePoliciesFixture.facultyStandard().getId();
    useFallbackPolicies(loanPolicy.getId(), requestPolicyId, noticePolicyId, overdueFinePolicyId);

    return loanPolicy;
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyEntry(int duration, String intervalId) {
    return new LoanPolicyBuilder()
      .withName(LOAN_POLICY_NAME)
      .withDescription("LoanPolicy")
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

}
