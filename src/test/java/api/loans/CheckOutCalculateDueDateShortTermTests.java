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
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.hamcrest.MatcherAssert.assertThat;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.junit.jupiter.api.Test;
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
class CheckOutCalculateDueDateShortTermTests extends APITests {

  private static final String LOAN_POLICY_NAME = "Move to the end of the current service point hours";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";
  private static final LocalTime TEST_TIME_MORNING = LocalTime.of(11, 0);

  private final String dueDateManagement =
    DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS.getValue();

  @Test
  void testRespectSelectedTimezoneForDueDateCalculations() {
    String expectedTimeZone = "America/New_York";
    int duration = 24;

    localeFixture.createNewYorkLocaleSettings();

    ZonedDateTime loanDate = ZonedDateTime.of(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE,
      TEST_TIME_MORNING, ZoneId.of(expectedTimeZone));
    ZonedDateTime expectedDueDate = atStartOfDay(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE, ZoneId.of(expectedTimeZone))
      .plusDays(1);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  void testRespectUtcTimezoneForDueDateCalculations() {
    int duration = 24;

    localeFixture.createUtcLocaleSettings();

    ZonedDateTime loanDate = ZonedDateTime.of(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE,
      TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = atStartOfDay(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE, UTC)
      .plusDays(1);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  void testMoveToTheEndOfCurrentServicePointHoursRolloverScenario() {
    int duration = 18;

    localeFixture.createUtcLocaleSettings();

    ZonedDateTime loanDate = ZonedDateTime.of(CASE_CURRENT_IS_OPEN_CURR_DAY, TEST_TIME_MORNING, UTC);

    ZonedDateTime expectedDueDate = ZonedDateTime.of(CASE_CURRENT_IS_OPEN_CURR_DAY, LocalTime.MIDNIGHT.plusHours(3), UTC);

    checkOffsetTime(loanDate, expectedDueDate, ROLLOVER_SCENARIO_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  @Test
  void testMoveToTheEndOfCurrentServicePointHoursNextDayIsClosed() {
    int duration = 1;

    localeFixture.createUtcLocaleSettings();

    ZonedDateTime loanDate = ZonedDateTime.of(CASE_CURRENT_IS_OPEN_CURR_DAY, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = atEndOfDay(CASE_CURRENT_IS_OPEN_CURR_DAY, UTC);

    checkOffsetTime(loanDate, expectedDueDate, ROLLOVER_SCENARIO_NEXT_DAY_CLOSED_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: open allDay
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testHoursLoanPeriodIfCurrentDayIsClosedAndNextAllDayOpen() {
    int duration = 24;

    ZonedDateTime loanDate = ZonedDateTime.of(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE, TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = atStartOfDay(CASE_FRI_SAT_MON_DAY_ALL_PREV_DATE.plusDays(1), UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID, INTERVAL_HOURS, duration);
  }

  /**
   * Loan period: Hours
   * Current day: closed
   * Next and prev day: period
   * Test period: FRI=open, SAT=close, MON=open
   */
  @Test
  void testHoursLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() {
    ZonedDateTime loanDate = ZonedDateTime.of(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY,
      TEST_TIME_MORNING, UTC);
    ZonedDateTime expectedDueDate = ZonedDateTime.of(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY,
      END_TIME_FIRST_PERIOD, UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_FRI_SAT_MON_SERVICE_POINT_ID, INTERVAL_HOURS, 25);
  }

  /**
   * Loan period: Minutes
   * Current day: open
   * Next and prev day: period
   */
  @Test
  void testMinutesLoanPeriodIfCurrentDayIsClosedAndNextDayHasPeriod() {
    ZonedDateTime loanDate = ZonedDateTime.of(CASE_CURRENT_IS_OPEN_PREV_DAY,
      END_TIME_FIRST_PERIOD, UTC).minusHours(1);
    ZonedDateTime expectedDueDate = ZonedDateTime.of(CASE_CURRENT_IS_OPEN_PREV_DAY,
      END_TIME_FIRST_PERIOD, UTC);

    checkOffsetTime(loanDate, expectedDueDate, CASE_CURRENT_IS_OPEN, INTERVAL_MINUTES, 90);
  }

  /**
   * Check result
   */
  private void checkOffsetTime(ZonedDateTime loanDate, ZonedDateTime expectedDueDate,
                               String servicePointId, String interval, int duration) {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();

    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(duration, interval);
    final IndividualResource loanPolicy = createLoanPolicy(loanPolicyEntry);
    UUID requestPolicyId = requestPoliciesFixture.allowAllRequestPolicy().getId();
    UUID noticePolicyId = noticePoliciesFixture.activeNotice().getId();
    IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.facultyStandard();
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();
    useFallbackPolicies(loanPolicy.getId(), requestPolicyId, noticePolicyId,
      overdueFinePolicy.getId(), lostItemFeePolicy.getId());

    mockClockManagerToReturnFixedDateTime(loanDate);
    final IndividualResource response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    mockClockManagerToReturnDefaultDateTime();

    final JsonObject loan = response.getJson();

    loanHasLoanPolicyProperties(loan, loanPolicy);
    loanHasOverdueFinePolicyProperties(loan,  overdueFinePolicy);
    loanHasLostItemPolicyProperties(loan,  lostItemFeePolicy);

    ZonedDateTime actualDueDate = getThresholdDateTime(parseDateTime(loan.getString("dueDate")));
    ZonedDateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);

    assertThat("due date should be " + thresholdDateTime + ", actual due date is " + actualDueDate,
      isSameMillis(actualDueDate, thresholdDateTime));
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private ZonedDateTime getThresholdDateTime(ZonedDateTime dateTime) {
    return dateTime.truncatedTo(ChronoUnit.MINUTES);
  }

  private IndividualResource createLoanPolicy(JsonObject loanPolicyEntry) {
    return loanPoliciesFixture.create(loanPolicyEntry);
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
