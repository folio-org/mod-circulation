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
import org.junit.Test;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.APITestContext.END_OF_2019_DUE_DATE;
import static api.support.fixtures.CalendarExamples.*;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.folio.circulation.domain.policy.DueDateManagement.*;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeConstants.MINUTES_PER_HOUR;

public class CheckOutCalculateDueDateTests extends APITests {

  private static final String INTERVAL_MONTHS = "Months";
  private static final String INTERVAL_HOURS = "Hours";

  private static final String DUE_DATE_KEY = "dueDate";
  private static final String LOAN_POLICY_ID_KEY = "loanPolicyId";

  private static final String ERROR_MESSAGE_DUE_DATE = "due date should be ";
  private static final String ERROR_MESSAGE_LOAN_POLICY = "last loan policy should be stored";

  private static final int START_VAL = 1;

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
  public void testKeepCurrentDueDateLongTermLoans()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = new SplittableRandom().nextInt(START_VAL, 12);

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

    DateTime expectedDate = new DateTime(LocalDate.parse(WEDNESDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

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

    DateTime expectedDate = new DateTime(LocalDate.parse(WEDNESDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.parse(END_TIME_SECOND_PERIOD)).toString()).withZoneRetainFields(DateTimeZone.UTC);

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

    DateTime expectedDate = new DateTime(LocalDate.parse(FRIDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

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

    DateTime expectedDate = new DateTime(LocalDate.parse(FRIDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.parse(END_TIME_SECOND_PERIOD)).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_CURRENT_DAY
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfCurrentAllDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_CURRENT_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_CURRENT_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(THURSDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDate,
      loan.getString(DUE_DATE_KEY), isEquivalentTo(expectedDate));
  }

  /**
   * Scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = FIXED
   * Closed Library Due Date Management = MOVE_TO_THE_END_OF_THE_CURRENT_DAY
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   */
  @Test
  public void testMoveToEndOfCurrentDayFixed()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_CURRENT_DAY: FIXED",
        fixedDueDateScheduleId,
        MOVE_TO_THE_END_OF_THE_CURRENT_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat(ERROR_MESSAGE_LOAN_POLICY,
      loan.getString(LOAN_POLICY_ID_KEY), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(THURSDAY_DATE, DATE_TIME_FORMATTER)
      .atTime(LocalTime.parse(END_TIME_SECOND_PERIOD)).toString()).withZoneRetainFields(DateTimeZone.UTC);

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
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
      duration, expectedDueDate);
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
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
      duration, expectedDueDate);
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
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
      duration, expectedDueDate);
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
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
      duration, expectedDueDate);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the current day
   * <p>
   * Calendar allDay = true (exclude current day)
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * If SPID-1 is determined to be CLOSED for system-calculated due date (and timestamp as applicable for short-term loans)
   * Then the due date timestamp should be changed to the endTime current day for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 5;

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_CURRENT_DAY,
      duration, expectedDueDate);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the current day
   * <p>
   * Calendar allDay = false
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * If SPID-1 is determined to be CLOSED for system-calculated due date (and timestamp as applicable for short-term loans)
   * Then the due date timestamp should be changed to the endTime current day for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 5;

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_CURRENT_DAY,
      duration, expectedDueDate);
  }

  /**
   * Test scenario for Long-term loans:
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Months|Weeks|Days
   * Closed Library Due Date Management = Move to the end of the current day
   * <p>
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * If SPID-1 is determined to be CLOSED for system-calculated due date (and timestamp as applicable for short-term loans)
   * Then the due date timestamp should be changed to the endTime current day for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentDayCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    int duration = 2;

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, policyProfileName, MOVE_TO_THE_END_OF_THE_CURRENT_DAY,
      duration, expectedDueDate);
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
    final DateTime loanDate = DateTime.now().withZoneRetainFields(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = new SplittableRandom().nextInt(START_VAL, 12);

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
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = new SplittableRandom().nextInt(START_VAL, MINUTES_PER_HOUR);

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
   * Test scenario when Calendar API is unavailable
   */
  @Test
  public void testScenarioWhenCalendarApiIsUnavailable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    int duration = new SplittableRandom().nextInt(START_VAL, 12);
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

    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
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

  private void checkFixedDayOrTime(String servicePointId, String policyProfileName,
                                   DueDateManagement dueDateManagement,
                                   int duration, DateTime expectedDueDate)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(dueDateManagement.getValue(), true,
      policyProfileName, dueDateManagement.getValue(), duration, INTERVAL_MONTHS);
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

    DateTime actualDueDate = DateTime.parse(loan.getString(DUE_DATE_KEY));

    assertThat(ERROR_MESSAGE_DUE_DATE + expectedDueDate + ", actual due date is " + actualDueDate,
      actualDueDate.compareTo(expectedDueDate) == 0);
  }

  private DateTime getEndDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DATE_TIME_FORMATTER);

    if (allDay) {
      return getDateTimeOfEndDay(localDate);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();

      if (openingHours.isEmpty()) {
        return getDateTimeOfEndDay(localDate);
      }
      OpeningHour openingHour = openingHours.get(openingHours.size() - 1);
      LocalTime localTime = LocalTime.parse(openingHour.getEndTime());
      return new DateTime(LocalDateTime.of(localDate, localTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
    }
  }

  /**
   * Get the date with the end of the day
   */
  private DateTime getDateTimeOfEndDay(LocalDate localDate) {
    return new DateTime(localDate.atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);
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

    useLoanPolicyAsFallback(loanPolicy.getId());

    return loanPolicy.getId().toString();
  }
}
