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
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.APITestContext.END_OF_2019_DUE_DATE;
import static api.support.fixtures.CalendarExamples.CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.FRIDAY_DATE;
import static api.support.fixtures.CalendarExamples.THURSDAY_DATE;
import static api.support.fixtures.CalendarExamples.WEDNESDAY_DATE;
import static api.support.fixtures.CalendarExamples.getCurrentAndNextFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getFirstFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getLastFakeOpeningDayByServId;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.HOURS;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class CheckOutCalculateDueDateTests extends APITests {

  private static final String INTERVAL_MONTHS = "Months";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";

  private static final String DUE_DATE_KEY = "dueDate";
  private static final String LOAN_POLICY_ID_KEY = "loanPolicyId";

  private static final String ERROR_MESSAGE_DUE_DATE = "due date should be ";
  private static final String ERROR_MESSAGE_LOAN_POLICY = "last loan policy should be stored";

  private static final LocalTime TEST_TIME_MORNING = new LocalTime(10, 0);
  private static final DateTime TEST_DATE =
    new LocalDate(2019, 1, 1)
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Keep the current due date
   * <p>
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should remain unchanged from system calculated due date timestamp
   */
  @Test
  public void shouldKeepDueDateUntouchedWhenCalendarScheduleIsAbsent()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = TEST_DATE;
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = 2;

    String loanPolicyName = "Keep the current due date: Rolling";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue(),
      duration, INTERVAL_MONTHS);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    assertThat(ERROR_MESSAGE_DUE_DATE + duration,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(loanDate.plusMonths(duration)));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = Keep the current due date
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testKeepCurrentDueDateLongTermLoansFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("Keep the current due date: FIXED",
        fixedDueDateScheduleId,
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(new DateTime(2019, 1, 11, 14, 43, 54, DateTimeZone.UTC))
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    assertThat(ERROR_MESSAGE_DUE_DATE + END_OF_2019_DUE_DATE,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(END_OF_2019_DUE_DATE));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfPreviousAllOpenDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate =
      WEDNESDAY_DATE.toDateTime(END_OF_A_DAY, DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfPreviousOpenDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate =
      WEDNESDAY_DATE.toDateTime(END_OF_A_DAY, DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfNextAllOpenDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate =
      FRIDAY_DATE.toDateTime(END_OF_A_DAY, DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfNextOpenDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate =
      FRIDAY_DATE.toDateTime(END_OF_A_DAY, DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }


  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the previous open day
   * <p>
   * Calendar allDay = true (exclude current day)
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the latest SPID-1 endTime for the closest previous Open=true day for SPID-1
   */
  @Test
  public void testMoveToEndOfPreviousAllOpenDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 3;

    // get datetime of endDay
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
      duration, INTERVAL_MONTHS, expectedDueDate, false);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the previous open day
   * <p>
   * Calendar allDay = false
   * Calendar openingHour = [range time]
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the latest SPID-1 endTime for the closest previous Open=true day for SPID-1
   */
  @Test
  public void testMoveToEndOfPreviousOpenDayTime()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 2;

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
      duration, INTERVAL_MONTHS, expectedDueDate, false);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the next open day
   * <p>
   * Calendar allDay = true (exclude current day)
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the latest SPID-1 endTime for the closest next Open=true day for SPID-1
   */
  @Test
  public void testMoveToEndOfNextAllOpenDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 5;

    // get datetime of endDay
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
      duration, INTERVAL_MONTHS, expectedDueDate, false);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the next open day
   * <p>
   * Calendar allDay = false
   * Calendar openingHour = [range time]
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the latest SPID-1 endTime for the closest next Open=true day for SPID-1
   */
  @Test
  public void testMoveToEndOfNextOpenDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 5;

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
      duration, INTERVAL_MONTHS, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Hours
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * <p>
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   * (Note that the system needs to logically consider 'rollover' scenarios where the service point remains open
   * for a continuity of hours that flow from one system date into the next - for example,
   * a service point that remains open until 2AM; then reopens at 8AM. In such a scenario,
   * the system should consider the '...beginning of the next open service point hours' to be 8AM. <NEED TO COME BACK TO THIS
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointHours()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 5;

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, INTERVAL_HOURS, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Hours
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointHoursAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    String interval = INTERVAL_HOURS;
    int duration = 5;

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Hours
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointHoursAllDayCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    String interval = INTERVAL_HOURS;
    int duration = 5;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    LocalDate localDate = openingDays.get(1).getOpeningDay().getDate();
    DateTime loanDate = THURSDAY_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime dateTime = localDate.toDateTime(LocalTime.MIDNIGHT);
    DateTime expectedDueDate = dateTime.withZoneRetainFields(DateTimeZone.UTC);

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, interval, expectedDueDate, true);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = false
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointMinutesCase1()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, interval, expectedDueDate, true);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointMinutesAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = THURSDAY_DATE
      .toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, interval, expectedDueDate, true);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointMinutesAllDayCase1()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, DateTimeZone.UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, policyProfileName, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, interval, expectedDueDate, true);
  }

  /**
   * Scenario for Short-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Hours|Minutes
   * Closed Library Due Date Management = Keep the current due date/time
   * <p>
   * Expected result:
   * Then the due date timestamp should remain unchanged from system calculated due date timestamp
   */
  @Test
  public void testKeepCurrentDueDateShortTermLoans()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = TEST_DATE;
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = 1;

    String loanPolicyName = "Keep the current due date/time";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, INTERVAL_HOURS);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    assertThat(ERROR_MESSAGE_DUE_DATE + duration,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(loanDate.plusHours(duration)));
  }

  /**
   * Exception Scenario
   * When:
   * - Loanable = N
   */
  @Test
  public void testItemIsNotLoanable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = TEST_DATE;
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = 1;

    String loanPolicyName = "Loan Policy Exception Scenario";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, false,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue(),
      duration, "Minutes");
    createLoanPolicy(loanPolicyEntry);

    final Response response = loansFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    MatcherAssert.assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    MatcherAssert.assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Item is not loanable")));
  }

  /**
   * 1
   * Test scenario when Calendar API is unavailable
   */
  @Test
  public void testScenarioWhenCalendarApiIsUnavailable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = TEST_DATE;
    int duration = 1;
    String loanPolicyName = "Calendar API is unavailable";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, INTERVAL_HOURS);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(loanDate)
        .at(UUID.fromString(CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID)));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    assertThat(ERROR_MESSAGE_DUE_DATE + duration,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(loanDate.plusHours(duration)));
  }

  /**
   * Exception test scenario of Calendar API
   */
  @Test
  public void testScenarioWhenCalendarApiIsEmpty()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = TEST_DATE;
    int duration = 1;
    String loanPolicyName = "Calendar API is unavailable";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, INTERVAL_HOURS);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(loanDate)
        .at(UUID.fromString(CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID)));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    assertThat(ERROR_MESSAGE_DUE_DATE + duration,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(loanDate.plusHours(duration)));
  }


  private void checkFixedDayOrTime(DateTime loanDate, String servicePointId, String policyProfileName,
                                   DueDateManagement dueDateManagement, int duration, String interval,
                                   DateTime expectedDueDate, boolean isIncludeTime)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(dueDateManagement.getValue(), true,
      policyProfileName, dueDateManagement.getValue(), duration, interval);
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    if (isIncludeTime) {
      checkDateTime(expectedDueDate, loan);
    } else {
      DateTime actualDueDate = DateTime.parse(loan.getString(DUE_DATE_KEY));
      assertThat(ERROR_MESSAGE_DUE_DATE + expectedDueDate + ", actual due date is " + actualDueDate,
        actualDueDate.compareTo(expectedDueDate) == 0);
    }
  }

  /**
   * Check the day and dateTime
   */
  private void checkDateTime(DateTime expectedDueDate, JsonObject loan) {
    DateTime actualDueDate = getThresholdDateTime(DateTime.parse(loan.getString(DUE_DATE_KEY)));

    DateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);
    assertThat(ERROR_MESSAGE_DUE_DATE + thresholdDateTime + ", actual due date is " + actualDueDate,
      actualDueDate.compareTo(thresholdDateTime) == 0);
  }

  private DateTime findDateTimeInPeriod(OpeningDayPeriod currentDayPeriod, LocalTime offsetTime, LocalDate currentDate) {
    List<OpeningHour> openingHoursList = currentDayPeriod.getOpeningDay().getOpeningHour();

    boolean isInPeriod = false;
    LocalTime newOffsetTime = null;
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = openingHoursList.get(i).getStartTime();
      LocalTime startTimeSecond = openingHoursList.get(i + 1).getStartTime();
      if (offsetTime.isAfter(startTimeFirst) && offsetTime.isBefore(startTimeSecond)) {
        isInPeriod = true;
        newOffsetTime = startTimeSecond;
        break;
      } else {
        newOffsetTime = startTimeSecond;
      }
    }

    LocalTime localTime = Objects.isNull(newOffsetTime) ? offsetTime.withMinuteOfHour(0) : newOffsetTime;
    return currentDate.toDateTime(isInPeriod ? localTime : offsetTime).withZoneRetainFields(DateTimeZone.UTC);
  }

  private DateTime getStartDateTimeOpeningDayRollover(List<OpeningDayPeriod> openingDays, String interval, int duration) {
    OpeningDayPeriod currentDayPeriod = openingDays.get(0);
    OpeningDayPeriod nextDayPeriod = openingDays.get(1);

    if (interval.equalsIgnoreCase(HOURS.name())) {
      if (currentDayPeriod.getOpeningDay().getAllDay()) {
        DateTime dateTime = TEST_DATE.plusHours(duration);
        return dateTime.withZoneRetainFields(DateTimeZone.UTC);
      } else {
        LocalTime offsetTime = TEST_TIME_MORNING.plusHours(duration);
        LocalDate currentDate = currentDayPeriod.getOpeningDay().getDate();

        if (isInPeriodOpeningDay(currentDayPeriod.getOpeningDay().getOpeningHour(), offsetTime)) {
          return findDateTimeInPeriod(currentDayPeriod, offsetTime, currentDate);
        } else {
          OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
          LocalDate localDate = nextOpeningDay.getDate();

          if (nextOpeningDay.getAllDay()) {
            return localDate.toDateTime(LocalTime.MIDNIGHT).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
            LocalTime startTime = openingHour.getStartTime();
            return localDate.toDateTime(startTime).withZoneRetainFields(DateTimeZone.UTC);
          }
        }
      }
    } else {
      OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
      LocalDate currentDate = currentOpeningDay.getDate();

      if (currentOpeningDay.getOpen()) {
        if (currentOpeningDay.getAllDay()) {
          DateTime currentEndDateTime = currentDate.toDateTime(END_OF_A_DAY);
          DateTime offsetDateTime =
            currentDate.toDateTime(TEST_TIME_MORNING)
              .plusMinutes(duration);

          if (isInCurrentDateTime(currentEndDateTime, offsetDateTime)) {
            return new DateTime(offsetDateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            LocalDate nextDate = nextOpeningDay.getDate();

            if (nextOpeningDay.getAllDay()) {
              return nextDate.toDateTime(LocalTime.MIDNIGHT).withZoneRetainFields(DateTimeZone.UTC);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = openingHour.getStartTime();
              return nextDate.toDateTime(startTime).withZoneRetainFields(DateTimeZone.UTC);
            }
          }
        } else {
          LocalTime offsetTime = TEST_TIME_MORNING.plusMinutes(duration);
          if (isInPeriodOpeningDay(currentOpeningDay.getOpeningHour(), offsetTime)) {
            return currentDate.toDateTime(offsetTime).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            LocalDate nextDate = nextOpeningDay.getDate();

            if (nextOpeningDay.getAllDay()) {
              return nextDate.toDateTime(LocalTime.MIDNIGHT).withZoneRetainFields(DateTimeZone.UTC);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = openingHour.getStartTime();
              return nextDate.toDateTime(startTime).withZoneRetainFields(DateTimeZone.UTC);
            }
          }
        }
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        LocalDate nextDate = nextOpeningDay.getDate();

        if (nextOpeningDay.getAllDay()) {
          return nextDate.toDateTime(LocalTime.MIDNIGHT).withZoneRetainFields(DateTimeZone.UTC);
        }
        OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
        LocalTime startTime = openingHour.getStartTime();
        return nextDate.toDateTime(startTime).withZoneRetainFields(DateTimeZone.UTC);
      }
    }
  }


  /**
   * Determine whether time is in any of the periods
   */
  public static boolean isInPeriodOpeningDay(List<OpeningHour> openingHoursList, LocalTime timeShift) {
    return openingHoursList.stream()
      .anyMatch(hours -> isTimeInCertainPeriod(timeShift,
        hours.getStartTime(), hours.getEndTime()));
  }

  /**
   * Determine whether the `time` is within a period `startTime` and `endTime`
   */
  private static boolean isTimeInCertainPeriod(LocalTime time, LocalTime startTime, LocalTime endTime) {
    return !time.isBefore(startTime) && !time.isAfter(endTime);
  }

  /**
   * Minor threshold when comparing minutes or milliseconds of dateTime
   */
  private DateTime getThresholdDateTime(DateTime dateTime) {
    return dateTime
      .withSecondOfMinute(0)
      .withMillisOfSecond(0);
  }

  private DateTime getEndDateTimeOpeningDay(OpeningDay openingDay) {
    LocalDate date = openingDay.getDate();
    return date.toDateTime(END_OF_A_DAY, DateTimeZone.UTC);
  }

  private DateTime getStartDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    LocalDate date = openingDay.getDate();

    if (allDay) {
      return getDateTimeOfStartDay(date);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();

      if (openingHours.isEmpty()) {
        return getDateTimeOfStartDay(date);
      }
      OpeningHour openingHour = openingHours.get(0);
      LocalTime localTime = openingHour.getStartTime();
      return date.toDateTime(localTime).withZoneRetainFields(DateTimeZone.UTC);
    }
  }

  /**
   * Determine whether the offset date is in the time period of the incoming current date
   *
   * @param currentDateTime incoming DateTime
   * @param offsetDateTime  DateTime with some offset days / hour / minutes
   * @return true if offsetDateTime is contains offsetDateTime in the time period
   */
  private boolean isInCurrentDateTime(DateTime currentDateTime, DateTime offsetDateTime) {
    return offsetDateTime.isBefore(currentDateTime) || offsetDateTime.isEqual(currentDateTime);
  }

  /**
   * Get the date with the start of the day
   */
  private DateTime getDateTimeOfStartDay(LocalDate localDate) {
    return localDate.toDateTime(LocalTime.MIDNIGHT).withZoneRetainFields(DateTimeZone.UTC);
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyEntry(String name, boolean loanable,
                                           String profileId, String dueDateManagement,
                                           int duration, String intervalId) {

    return new LoanPolicyBuilder()
      .withName(name)
      .withDescription("LoanPolicy")
      .withLoanable(loanable)
      .withLoansProfile(profileId)
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

  /**
   * Create a fake json LoanPolicy for fixed period
   */
  private JsonObject createLoanPolicyEntryFixed(String name,
                                                UUID fixedDueDateScheduleId,
                                                String dueDateManagement) {
    return new LoanPolicyBuilder()
      .withName(name)
      .withDescription("New LoanPolicy")
      .fixed(fixedDueDateScheduleId)
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

  private String createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyEntry);

    useLoanPolicyAsFallback(
      loanPolicy.getId(),
      requestPoliciesFixture.noAllowedTypes().getId(),
      noticePoliciesFixture.activeNotice().getId()
    );

    return loanPolicy.getId().toString();
  }
}
