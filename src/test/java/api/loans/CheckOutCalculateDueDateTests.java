package api.loans;

import static api.APITestSuite.END_OF_2019_DUE_DATE;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_WED_THU_FRI_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.FRIDAY_DATE;
import static api.support.fixtures.CalendarExamples.THURSDAY_DATE;
import static api.support.fixtures.CalendarExamples.WEDNESDAY_DATE;
import static api.support.fixtures.CalendarExamples.getCurrentAndNextFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getCurrentFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getFirstFakeOpeningDayByServId;
import static api.support.fixtures.CalendarExamples.getLastFakeOpeningDayByServId;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID;
import static api.support.fixtures.LibraryHoursExamples.CASE_CLOSED_LIBRARY_SERVICE_POINT_ID;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.HOURS;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;
import static org.folio.circulation.support.PeriodUtil.calculateOffset;
import static org.folio.circulation.support.PeriodUtil.calculateOffsetTime;
import static org.folio.circulation.support.PeriodUtil.isInCurrentLocalDateTime;
import static org.folio.circulation.support.PeriodUtil.isOffsetTimeInCurrentDayPeriod;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningDayPeriod;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.LoanPolicyPeriod;
import org.folio.circulation.domain.policy.LoansPolicyProfile;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckOutCalculateDueDateTests extends APITests {

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
    int duration = new SplittableRandom().nextInt(1, 12);

    String loanPolicyName = "Keep the current due date: Rolling";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue(),
      duration, "Months");
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

    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusMonths(duration)));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("Keep the current due date: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(new DateTime(2019, 1, 11, 14, 43, 54, DateTimeZone.UTC))
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    assertThat("due date should be " + END_OF_2019_DUE_DATE,
      loan.getString("dueDate"), isEquivalentTo(END_OF_2019_DUE_DATE));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(WEDNESDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(WEDNESDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.parse("19:00")).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(FRIDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(FRIDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.parse("19:00")).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_CURRENT_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_CURRENT_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(THURSDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.MAX).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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

    String fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().toString();

    String loanPolicyId = createLoanPolicy(
      createLoanPolicyEntryFixed("MOVE_TO_THE_END_OF_THE_CURRENT_DAY: FIXED",
        fixedDueDateScheduleId,
        LoansPolicyProfile.FIXED.name(),
        DueDateManagement.MOVE_TO_THE_END_OF_THE_CURRENT_DAY.getValue()));

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(THURSDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.parse("19:00")).toString()).withZoneRetainFields(DateTimeZone.UTC);

    assertThat("due date should be " + expectedDate,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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
    String loanPolicyName = "MoveToEndOfPreviousAllOpenDay";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY;
    int duration = 3;
    String interval = "Months";

    // get datetime of endDay
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "MoveToEndOfPreviousOpenDay";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY;
    int duration = 2;
    String interval = "Months";

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "MoveToEndOfPreviousOpenDay";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY;
    int duration = 5;
    String interval = "Months";

    // get datetime of endDay
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the end of the next open day";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY;
    int duration = 5;
    String interval = "Months";

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the end of the current day";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_CURRENT_DAY;
    int duration = 5;
    String interval = "Months";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the end of the current day";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_CURRENT_DAY;
    int duration = 5;
    String interval = "Months";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the end of the current day";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_THE_END_OF_THE_CURRENT_DAY;
    int duration = 2;
    String interval = "Months";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Hours
   * Closed Library Due Date Management = Move to the end of the current service point hours
   * <p>
   * Calendar allDay = true (exclude current day)
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentServicePointHoursAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the end of the current service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS;
    int duration = 2;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Hours
   * Closed Library Due Date Management = Move to the end of the current service point hours
   * <p>
   * Calendar allDay = true (exclude current day)
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentServicePointHoursAllDayCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the end of the current service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS;
    int duration = 2;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Hours
   * Closed Library Due Date Management = Move to the end of the current service point hours
   * <p>
   * Calendar allDay = false
   * Test period: FRI=open, SAT=close, MON=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentServicePointHours()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the end of the current service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS;
    int duration = 5;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = X Hours
   * Closed Library Due Date Management = Move to the end of the current service point hours
   * <p>
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the endTime of the current service point for SPID-1 (i.e., truncating the loan length)
   */
  @Test
  public void testMoveToEndOfCurrentServicePointHoursCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the end of the current service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_END_OF_CURRENT_SERVICE_POINT_HOURS;
    int duration = 5;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getCurrentFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 5;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Hours
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointHoursCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 1;
    String interval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 5;
    String interval = "Hours";

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, false);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 5;
    String interval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    String currentDate = openingDays.get(0).getOpeningDay().getDate();
    LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    LocalDateTime localDateTime = localDate.atTime(LocalTime.now(ZoneOffset.UTC))
      .plusHours(duration);
    DateTime expectedDueDate = new DateTime(localDateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testMoveToBeginningOfNextOpenServicePointMinutes()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
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
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, true);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: FRI=open, SAT=close, MON=open
   * Test offset period: 1 Hour
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsOffsetInterval()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";
    int offsetDuration = 1;
    String offsetInterval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration).plusHours(offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: FRI=open, SAT=close, MON=open
   * Test offset period: 1 Hour
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsMinutes()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";
    int offsetDuration = 1;
    String offsetInterval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration).plusHours(offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = false
   * Test period: FRI=open, SAT=close, MON=open
   * Test offset period: 45 minutes
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsMinutesCase1()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";
    int offsetDuration = 45;
    String offsetInterval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration).plusMinutes(offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = false
   * Test period: WED=open, THU=open, FRI=open
   * Test offset period: 60 minutes
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsMinutesCase2()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";
    int offsetDuration = 60;
    String offsetInterval = "Minutes";

    OpeningDayPeriod currentFakePeriod = getCurrentFakeOpeningDayByServId(servicePointId);

    DateTime expectedDueDate = getRolloverForMinutesPeriod(duration, currentFakePeriod, getLastFakeOpeningDayByServId(servicePointId),
      currentFakePeriod.getOpeningDay(), currentFakePeriod.getOpeningDay().getDate(), offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   * Test offset period: 35 minutes
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsMinutesAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 30;
    String interval = "Minutes";
    int offsetDuration = 35;
    String offsetInterval = "Minutes";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration)
      .plusMinutes(offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Random Hours
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   * Test offset period: Random Hours
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsHoursAllDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = new SplittableRandom().nextInt(1, 10);
    String interval = "Hours";
    int offsetDuration = new SplittableRandom().nextInt(1, 24);
    String offsetInterval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    OpeningDayPeriod openingDayPeriod = openingDays.get(0);
    String currentDate = openingDayPeriod.getOpeningDay().getDate();
    LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    LocalDateTime localDateTime = localDate.atTime(LocalTime.now(ZoneOffset.UTC))
      .plusHours(duration)
      .plusHours(offsetDuration);
    DateTime expectedDueDate = new DateTime(localDateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Rolling
   * Loan period = Minutes
   * Closed Library Due Date Management = Move to the beginning of the next open service point hours
   * Calendar allDay = true
   * Test period: WED=open, THU=open, FRI=open
   * Test offset period: 2 Hours
   * <p>
   * Expected result:
   * Then the due date timestamp should be changed to the earliest SPID-1 startTime for the closest next Open=true available hours for SPID-1
   */
  @Test
  public void testAdditionalLoanPolicyConfigurationsHours()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    String servicePointId = CASE_WED_THU_FRI_SERVICE_POINT_ID;
    String loanPolicyName = "Move to the beginning of the next open service point hours";
    String policyProfileName = LoansPolicyProfile.ROLLING.name();
    DueDateManagement dueDateManagement = DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
    int duration = 2;
    String interval = "Hours";
    int offsetDuration = 0;
    String offsetInterval = "Hours";

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime expectedDueDate = getRolloverForHourlyPeriod(duration, openingDays.get(0), openingDays.get(1), offsetDuration)
      .plusHours(offsetDuration);

    checkDayWithOffsetTime(servicePointId, loanPolicyName,
      policyProfileName, dueDateManagement, duration, interval, expectedDueDate, offsetDuration, offsetInterval);
  }

  /**
   * Test scenario when Library is close in current day
   * Library Yours: 1 period (end today)
   * Loan period = Random Hours
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Given any scenarios wherein Closed Library Due Date Management configurations will lead to a due date timestamp
   * that falls outside of the range of a Fixed due date schedule,
   * then the loan period should be truncated to the closing time
   * for the service point on the endDate of the appropriate Fixed Due Date Schedule
   */
  @Test
  public void testAdditionalScenarioWhenClosedLibraryTodayHours()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    int duration = 1;

    String loanPolicyName = "Closed Library";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, "Hours");
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(loanDate)
        .at(UUID.fromString(CASE_CLOSED_LIBRARY_SERVICE_POINT_ID)));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(THURSDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
  }

  /**
   * Test scenario when Library is close in current day
   * Library Yours: 1 periods (end today)
   * Loan period = X Months
   * Test period: WED=open, THU=open, FRI=open
   * <p>
   * Expected result:
   * Given any scenarios wherein Closed Library Due Date Management configurations will lead to a due date timestamp
   * that falls outside of the range of a Fixed due date schedule,
   * then the loan period should be truncated to the closing time
   * for the service point on the endDate of the appropriate Fixed Due Date Schedule
   */
  @Test
  public void testAdditionalScenarioWhenClosedLibraryTodayDay()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    int duration = new SplittableRandom().nextInt(1, 2);

    String loanPolicyName = "Closed Library";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue(),
      duration, "Days");
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(loanDate)
        .at(UUID.fromString(CASE_CLOSED_LIBRARY_IN_THU_SERVICE_POINT_ID)));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    DateTime expectedDate = new DateTime(LocalDate.parse(WEDNESDAY_DATE, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER))
      .atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(expectedDate));
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
    int duration = new SplittableRandom().nextInt(1, 12);

    String loanPolicyName = "Keep the current due date/time";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, "Hours");
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

    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusHours(duration)));
  }

  /**
   * Exception Scenario
   * When:
   * - Loanable = N
   */
  @Test
  public void testExceptionScenario()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = new SplittableRandom().nextInt(1, 60);

    String loanPolicyName = "Loan Policy Exception Scenario";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, false,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE.getValue(),
      duration, "Minutes");
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

    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusMinutes(duration)));
  }

  /**
   * Test scenario when Calendar API is unavailable
   */
  @Test
  public void testScenarioWhenCalendarApiIsUnavailable()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    int duration = new SplittableRandom().nextInt(1, 12);
    String loanPolicyName = "Calendar API is unavailable";
    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      LoansPolicyProfile.ROLLING.name(), DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(),
      duration, "Hours");
    String loanPolicyId = createLoanPolicy(loanPolicyEntry);

    final IndividualResource response = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(loanDate)
        .at(UUID.fromString(CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID)));

    final JsonObject loan = response.getJson();

    assertThat("last loan policy should be stored",
      loan.getString("loanPolicyId"), is(loanPolicyId));

    assertThat("due date should be " + duration,
      loan.getString("dueDate"), isEquivalentTo(loanDate.plusHours(duration)));
  }

  private void checkFixedDayOrTime(String servicePointId, String loanPolicyName,
                                   String policyProfileName, DueDateManagement dueDateManagement,
                                   int duration, String interval, DateTime expectedDueDate, boolean isIncludeTime)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyEntry(loanPolicyName, true,
      policyProfileName, dueDateManagement.getValue(), duration, interval);
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

    if (isIncludeTime) {
      checkDateTime(expectedDueDate, loan);
    } else {
      DateTime actualDueDate = DateTime.parse(loan.getString("dueDate"));
      assertThat("due date should be " + expectedDueDate + ", actual due date is " + actualDueDate,
        actualDueDate.compareTo(expectedDueDate) == 0);
    }
  }

  private void checkDayWithOffsetTime(String servicePointId, String loanPolicyName,
                                      String policyProfileName, DueDateManagement dueDateManagement,
                                      int duration, String interval, DateTime expectedDueDate,
                                      int offsetDuration, String offsetInterval)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final DateTime loanDate = DateTime.now().toDateTime(DateTimeZone.UTC);
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    JsonObject loanPolicyEntry = createLoanPolicyOffsetTimeEntry(loanPolicyName, policyProfileName,
      dueDateManagement.getValue(), duration, interval, offsetDuration, offsetInterval);
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

    checkDateTime(expectedDueDate, loan);
  }

  /**
   * Checl the day and dateTime
   */
  private void checkDateTime(DateTime expectedDueDate, JsonObject loan) {
    DateTime actualDueDate = getThresholdDateTime(DateTime.parse(loan.getString("dueDate")));

    assertThat("due date day should be " + expectedDueDate.getDayOfWeek() + " day of week",
      actualDueDate.getDayOfWeek() == expectedDueDate.getDayOfWeek());

    DateTime thresholdDateTime = getThresholdDateTime(expectedDueDate);
    assertThat("due date should be " + thresholdDateTime + ", actual due date is " + actualDueDate,
      actualDueDate.compareTo(thresholdDateTime) == 0);
  }

  /**
   * Test scenario for Short-term loans
   * Loanable = Y
   * Loan profile = Fixed
   */
  private DateTime getRolloverForMinutesPeriod(int duration, OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod,
                                               OpeningDay currentOpeningDay, String currentDate, int offsetDuration) {

    if (currentOpeningDay.getAllDay()) {
      LocalDate currentLocalDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      LocalDateTime currentEndLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.MAX);
      LocalDateTime offsetLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.now(ZoneOffset.UTC)).plusMinutes(duration);

      if (isInCurrentLocalDateTime(currentEndLocalDateTime, offsetLocalDateTime)) {
        return calculateOffset(offsetLocalDateTime, LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        String nextDate = nextOpeningDay.getDate();
        LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

        if (nextOpeningDay.getAllDay()) {
          return calculateOffset(localDate.atTime(LocalTime.MIN), LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
        } else {
          OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
          LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
          return calculateOffset(LocalDateTime.of(localDate, startTime), LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
        }
      }
    } else {
      LocalTime offsetTime = LocalTime.now(ZoneOffset.UTC).plusMinutes(duration);
      if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
        LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
        return calculateOffset(LocalDateTime.of(localDate, offsetTime), LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        String nextDate = nextOpeningDay.getDate();
        LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

        if (nextOpeningDay.getAllDay()) {
          return calculateOffset(localDate.atTime(LocalTime.MIN), LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
        } else {
          OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
          LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
          return calculateOffset(LocalDateTime.of(localDate, startTime), LoanPolicyPeriod.MINUTES, offsetDuration).withZoneRetainFields(DateTimeZone.UTC);
        }
      }
    }
  }

  private DateTime findDateTimeInPeriod(OpeningDayPeriod currentDayPeriod, LocalTime offsetTime, String currentDate) {
    List<OpeningHour> openingHoursList = currentDayPeriod.getOpeningDay().getOpeningHour();

    LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
    boolean isInPeriod = false;
    LocalTime newOffsetTime = null;
    for (int i = 0; i < openingHoursList.size() - 1; i++) {
      LocalTime startTimeFirst = LocalTime.parse(openingHoursList.get(i).getStartTime());
      LocalTime startTimeSecond = LocalTime.parse(openingHoursList.get(i + 1).getStartTime());
      if (offsetTime.isAfter(startTimeFirst) && offsetTime.isBefore(startTimeSecond)) {
        isInPeriod = true;
        newOffsetTime = startTimeSecond;
        break;
      } else {
        newOffsetTime = startTimeSecond;
      }
    }

    LocalTime localTime = Objects.isNull(newOffsetTime) ? offsetTime.withMinute(0) : newOffsetTime;
    return new DateTime(LocalDateTime.of(localDate, isInPeriod ? localTime : offsetTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
  }


  private DateTime getRolloverForHourlyPeriod(int duration, OpeningDayPeriod currentDayPeriod, OpeningDayPeriod nextDayPeriod, int offsetDuration) {

    if (currentDayPeriod.getOpeningDay().getAllDay()) {
      String currentDate = currentDayPeriod.getOpeningDay().getDate();
      LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
      LocalDateTime localDateTime = localDate.atTime(LocalTime.now(ZoneOffset.UTC)).plusHours(duration);
      return calculateOffset(localDateTime, LoanPolicyPeriod.HOURS, offsetDuration);
    } else {
      LocalTime offsetTime = calculateOffsetTime(LocalTime.now(ZoneOffset.UTC).plusHours(duration), LoanPolicyPeriod.HOURS, offsetDuration);
      String currentDate = currentDayPeriod.getOpeningDay().getDate();

      if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
        return findDateTimeInPeriod(currentDayPeriod, offsetTime, currentDate);
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        String nextDate = nextOpeningDay.getDate();
        LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

        if (nextOpeningDay.getAllDay()) {
          return new DateTime(localDate.atTime(LocalTime.MIN).toString());
        } else {
          OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
          LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
          return new DateTime(LocalDateTime.of(localDate, startTime).toString());
        }
      }
    }
  }

  private DateTime getStartDateTimeOpeningDayRollover(List<OpeningDayPeriod> openingDays, String interval, int duration) {
    OpeningDayPeriod currentDayPeriod = openingDays.get(0);
    OpeningDayPeriod nextDayPeriod = openingDays.get(1);

    if (interval.equalsIgnoreCase(HOURS.name())) {
      if (currentDayPeriod.getOpeningDay().getAllDay()) {
        LocalDateTime localDateTime = LocalDateTime.now(ZoneOffset.UTC).plusHours(duration);
        return new DateTime(localDateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
      } else {
        LocalTime offsetTime = LocalTime.now(ZoneOffset.UTC).plusHours(duration);
        String currentDate = currentDayPeriod.getOpeningDay().getDate();

        if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
          return findDateTimeInPeriod(currentDayPeriod, offsetTime, currentDate);
        } else {
          OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
          String nextDate = nextOpeningDay.getDate();
          LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

          if (nextOpeningDay.getAllDay()) {
            return new DateTime(localDate.atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
            LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
            return new DateTime(LocalDateTime.of(localDate, startTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
          }
        }
      }
    } else {
      OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
      String currentDate = currentOpeningDay.getDate();

      if (currentOpeningDay.getOpen()) {
        if (currentOpeningDay.getAllDay()) {
          LocalDate currentLocalDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
          LocalDateTime currentEndLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.MAX);
          LocalDateTime offsetLocalDateTime = LocalDateTime.of(currentLocalDate, LocalTime.now(ZoneOffset.UTC)).plusMinutes(duration);

          if (isInCurrentLocalDateTime(currentEndLocalDateTime, offsetLocalDateTime)) {
            return new DateTime(offsetLocalDateTime.toString()).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            String nextDate = nextOpeningDay.getDate();
            LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

            if (nextOpeningDay.getAllDay()) {
              return new DateTime(localDate.atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
              return new DateTime(LocalDateTime.of(localDate, startTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
            }
          }
        } else {
          LocalTime offsetTime = LocalTime.now(ZoneOffset.UTC).plusMinutes(duration);
          if (isOffsetTimeInCurrentDayPeriod(currentDayPeriod, offsetTime)) {
            LocalDate localDate = LocalDate.parse(currentDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));
            return new DateTime(LocalDateTime.of(localDate, offsetTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            String nextDate = nextOpeningDay.getDate();
            LocalDate localDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

            if (nextOpeningDay.getAllDay()) {
              return new DateTime(localDate.atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
              return new DateTime(LocalDateTime.of(localDate, startTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
            }
          }
        }
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        String nextDate = nextOpeningDay.getDate();
        LocalDate nextLocalDate = LocalDate.parse(nextDate, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

        if (nextOpeningDay.getAllDay()) {
          return new DateTime(nextLocalDate.atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
        }
        OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
        LocalTime startTime = LocalTime.parse(openingHour.getStartTime());
        return new DateTime(LocalDateTime.of(nextLocalDate, startTime).toString()).withZoneRetainFields(DateTimeZone.UTC);
      }
    }
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
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

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

  private DateTime getStartDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    String date = openingDay.getDate();
    LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DATE_TIME_FORMATTER));

    if (allDay) {
      return getDateTimeOfStartDay(localDate);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();

      if (openingHours.isEmpty()) {
        return getDateTimeOfStartDay(localDate);
      }
      OpeningHour openingHour = openingHours.get(0);
      LocalTime localTime = LocalTime.parse(openingHour.getStartTime());
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
   * Get the date with the start of the day
   */
  private DateTime getDateTimeOfStartDay(LocalDate localDate) {
    return new DateTime(localDate.atTime(LocalTime.MIN).toString()).withZoneRetainFields(DateTimeZone.UTC);
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyEntry(String name, boolean loanable,
                                           String profileId, String dueDateManagement,
                                           int duration, String intervalId) {
    return new JsonObject()
      .put("name", name)
      .put("description", "LoanPolicy")
      .put("loanable", loanable)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", profileId)
        .put("period", new JsonObject().put("duration", duration).put("intervalId", intervalId))
        .put("closedLibraryDueDateManagementId", dueDateManagement))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));
  }

  /**
   * Create a fake json LoanPolicy for fixed period
   */
  private JsonObject createLoanPolicyEntryFixed(String name, String fixedDueDateScheduleId,
                                                String profileId, String dueDateManagement) {
    return new JsonObject()
      .put("name", name)
      .put("description", "New LoanPolicy")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", profileId)
        .put("closedLibraryDueDateManagementId", dueDateManagement)
        .put("fixedDueDateScheduleId", fixedDueDateScheduleId)
      )
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));
  }

  /**
   * Create a fake json LoanPolicy
   */
  private JsonObject createLoanPolicyOffsetTimeEntry(String name, String profileId, String dueDateManagement,
                                                     int duration, String intervalId,
                                                     int offsetDuration, String offsetInterval) {
    JsonObject period = new JsonObject()
      .put("duration", duration)
      .put("intervalId", intervalId);

    JsonObject openingTimeOffset = new JsonObject()
      .put("duration", offsetDuration)
      .put("intervalId", offsetInterval);

    return new JsonObject()
      .put("name", name)
      .put("description", "Full LoanPolicy")
      .put("loanable", true)
      .put("renewable", true)
      .put("loansPolicy", new JsonObject()
        .put("profileId", profileId)
        .put("period", period)
        .put("openingTimeOffset", openingTimeOffset)
        .put("closedLibraryDueDateManagementId", dueDateManagement))
      .put("renewalsPolicy", new JsonObject()
        .put("renewFromId", "CURRENT_DUE_DATE")
        .put("differentPeriod", false));
  }

  private String createLoanPolicy(JsonObject loanPolicyEntry)
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    IndividualResource resource = loanPoliciesFixture.create(loanPolicyEntry);

    useLoanPolicyAsFallback(resource.getId());

    return resource.getId().toString();
  }
}
