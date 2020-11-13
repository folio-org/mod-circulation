package api.loans;

import static api.support.APITestContext.END_OF_CURRENT_YEAR_DUE_DATE;
import static api.support.ExampleTimeZones.NEW_YORK;
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
import static api.support.fixtures.ConfigurationExample.newYorkTimezoneConfiguration;
import static api.support.fixtures.ConfigurationExample.utcTimezoneConfiguration;
import static api.support.fixtures.LibraryHoursExamples.CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID;
import static api.support.matchers.DateTimeMatchers.isEquivalentTo;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE_TIME;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY;
import static org.folio.circulation.domain.policy.DueDateManagement.MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY;
import static org.folio.circulation.domain.policy.LoanPolicyPeriod.HOURS;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.support.utils.DateTimeUtil.toStartOfDayDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.toUtcDateTime;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.LocalTime.MIDNIGHT;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.folio.circulation.domain.OpeningDay;
import org.folio.circulation.domain.OpeningHour;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.junit.Test;

import api.support.APITests;
import api.support.OpeningDayPeriod;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutCalculateDueDateTests extends APITests {
  private static final String INTERVAL_MONTHS = "Months";
  private static final String INTERVAL_HOURS = "Hours";
  private static final String INTERVAL_MINUTES = "Minutes";

  private static final LocalTime TEST_TIME_MORNING = new LocalTime(10, 0);
  private static final DateTime TEST_DATE =
    new LocalDate(2019, 1, 1)
      .toDateTime(TEST_TIME_MORNING, UTC);

  @Test
  public void testRespectSelectedTimezoneForDueDateCalculations() {
    configClient.create(newYorkTimezoneConfiguration());

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, KEEP_THE_CURRENT_DUE_DATE);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(currentYearDateTime(1, 11, 14, 43, 54, NEW_YORK))
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(),
      isEquivalentTo(currentYearDateTime(12, 31, 23, 59, 59, NEW_YORK)));
  }

  @Test
  public void testRespectUtcTimezoneForDueDateCalculations() {
    configClient.create(utcTimezoneConfiguration());

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, KEEP_THE_CURRENT_DUE_DATE);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(currentYearDateTime(1, 11, 14, 43, 54, UTC))
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(), isEquivalentTo(END_OF_CURRENT_YEAR_DUE_DATE));
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
  public void testKeepCurrentDueDateLongTermLoansFixed() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, KEEP_THE_CURRENT_DUE_DATE);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(currentYearDateTime(1, 11, 14, 43, 54, UTC))
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(), isEquivalentTo(END_OF_CURRENT_YEAR_DUE_DATE));
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
  public void testMoveToEndOfPreviousAllOpenDayFixed() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(),
      isEquivalentTo(WEDNESDAY_DATE.toDateTime(END_OF_A_DAY, UTC)));
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
  public void testMoveToEndOfPreviousOpenDayFixed() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(),
      isEquivalentTo(WEDNESDAY_DATE.toDateTime(END_OF_A_DAY, UTC)));
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
  public void testMoveToEndOfNextAllOpenDayFixed() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useLoanPolicy(createFixedLoanPolicy(fixedDueDateScheduleId,
      MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue()));

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(), isEquivalentTo(FRIDAY_DATE.toDateTime(END_OF_A_DAY, UTC)));
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
  public void testMoveToEndOfNextOpenDayFixed() {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(CASE_WED_THU_FRI_SERVICE_POINT_ID);

    UUID fixedDueDateScheduleId = loanPoliciesFixture
      .createExampleFixedDueDateSchedule().getId();

    useFixedPolicy(fixedDueDateScheduleId, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY);

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(), isEquivalentTo(FRIDAY_DATE.toDateTime(END_OF_A_DAY, UTC)));
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
  public void testMoveToEndOfPreviousAllOpenDay() {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 3;

    // get datetime of endDay
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
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
  public void testMoveToEndOfPreviousOpenDayTime() {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 2;

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getFirstFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY,
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
  public void testMoveToEndOfNextAllOpenDay() {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 5;

    // get datetime of endDay
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
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
  public void testMoveToEndOfNextOpenDay() {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 5;

    // get last datetime from hours period
    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getEndDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY,
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
  public void testMoveToBeginningOfNextOpenServicePointHours() {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 5;

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
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
  public void testMoveToBeginningOfNextOpenServicePointHoursAllDay() {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 5;

    OpeningDayPeriod openingDay = getLastFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDay(openingDay.getOpeningDay());

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, INTERVAL_HOURS, expectedDueDate, false);
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
  public void testMoveToBeginningOfNextOpenServicePointHoursAllDayCase2() {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = 5;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    LocalDate localDate = openingDays.get(1).getOpeningDay().getDate();
    DateTime loanDate = THURSDAY_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime dateTime = localDate.toDateTime(MIDNIGHT);
    DateTime expectedDueDate = dateTime.withZoneRetainFields(UTC);

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
      duration, INTERVAL_HOURS, expectedDueDate, true);
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
  public void testMoveToBeginningOfNextOpenServicePointMinutesCase1() {
    String servicePointId = CASE_FRI_SAT_MON_SERVICE_POINT_ID;
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY
      .toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
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
  public void testMoveToBeginningOfNextOpenServicePointMinutesAllDay() {
    String servicePointId = CASE_WED_THU_FRI_DAY_ALL_SERVICE_POINT_ID;
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate = THURSDAY_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
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
  public void testMoveToBeginningOfNextOpenServicePointMinutesAllDayCase1() {
    String servicePointId = CASE_FRI_SAT_MON_DAY_ALL_SERVICE_POINT_ID;
    int duration = 30;
    String interval = INTERVAL_MINUTES;

    List<OpeningDayPeriod> openingDays = getCurrentAndNextFakeOpeningDayByServId(servicePointId);
    DateTime loanDate =
      CASE_FRI_SAT_MON_DAY_ALL_CURRENT_DATE.toDateTime(TEST_TIME_MORNING, UTC);
    DateTime expectedDueDate = getStartDateTimeOpeningDayRollover(openingDays, interval, duration);

    checkFixedDayOrTime(loanDate, servicePointId, MOVE_TO_BEGINNING_OF_NEXT_OPEN_SERVICE_POINT_HOURS,
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
  public void testKeepCurrentDueDateShortTermLoans() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();
    int duration = 1;

    IndividualResource loanPolicy = useLoanPolicy(createLoanPolicy(
      "Keep the current due date/time", true,
        KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(), duration, INTERVAL_HOURS));

    IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.facultyStandard();
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePolicy.getId(), lostItemFeePolicy.getId());

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(TEST_DATE)
        .at(checkoutServicePointId));

    assertThat(response.getDueDate(), isEquivalentTo(TEST_DATE.plusHours(duration)));
  }

  /**
   * Exception Scenario
   * When:
   * - Loanable = N
   */
  @Test
  public void testItemIsNotLoanable() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.randomUUID();

    useLoanPolicy(createLoanPolicy(
      "Loan Policy Exception Scenario", false,
        KEEP_THE_CURRENT_DUE_DATE.getValue(), 1, "Minutes"));

    final Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(TEST_DATE)
        .at(checkoutServicePointId));

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Item is not loanable")));
  }

  @Test
  public void testScenarioWhenCalendarApiIsUnavailable() {
    useLoanPolicy(createLoanPolicy("Calendar API is unavailable", true,
      KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(), 1, INTERVAL_HOURS));

    Response response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(TEST_DATE)
        .at(UUID.fromString(CASE_CALENDAR_IS_UNAVAILABLE_SERVICE_POINT_ID)));

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Calendar open periods are not found"))));
  }

  @Test
  public void testScenarioWhenCalendarApiIsEmpty() {
    final var duration = 1;

    useLoanPolicy(createLoanPolicy(
      "Calendar API is unavailable", true,
        KEEP_THE_CURRENT_DUE_DATE_TIME.getValue(), duration, INTERVAL_HOURS));

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(itemsFixture.basedUponSmallAngryPlanet())
        .to(usersFixture.steve())
        .on(TEST_DATE)
        .at(UUID.fromString(CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID)));

    assertThat(response.getDueDate(), isEquivalentTo(TEST_DATE.plusHours(duration)));
  }

  private void checkFixedDayOrTime(DateTime loanDate, String servicePointId,
    DueDateManagement dueDateManagement, int duration, String interval,
    DateTime expectedDueDate, boolean isIncludeTime) {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource steve = usersFixture.steve();
    final UUID checkoutServicePointId = UUID.fromString(servicePointId);

    IndividualResource loanPolicy = useLoanPolicy(createLoanPolicy(
      dueDateManagement.getValue(), true,
        dueDateManagement.getValue(), duration, interval));

    IndividualResource overdueFinePolicy = overdueFinePoliciesFixture.facultyStandard();
    IndividualResource lostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard();

    useFallbackPolicies(
      loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePolicy.getId(),
      lostItemFeePolicy.getId());

    final var response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(steve)
        .on(loanDate)
        .at(checkoutServicePointId));

    if (isIncludeTime) {
      assertThat(response.getDueDate(), isEquivalentTo(getThresholdDateTime(expectedDueDate)));
    } else {
      assertThat(response.getDueDate(), isEquivalentTo(expectedDueDate));
    }
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
    return toUtcDateTime(currentDate, isInPeriod ? localTime : offsetTime);
  }

  private DateTime getStartDateTimeOpeningDayRollover(List<OpeningDayPeriod> openingDays, String interval, int duration) {
    OpeningDayPeriod currentDayPeriod = openingDays.get(0);
    OpeningDayPeriod nextDayPeriod = openingDays.get(1);

    if (interval.equalsIgnoreCase(HOURS.name())) {
      if (currentDayPeriod.getOpeningDay().getAllDay()) {
        DateTime dateTime = TEST_DATE.plusHours(duration);
        return dateTime.withZoneRetainFields(UTC);
      } else {
        LocalTime offsetTime = TEST_TIME_MORNING.plusHours(duration);
        LocalDate currentDate = currentDayPeriod.getOpeningDay().getDate();

        if (isInPeriodOpeningDay(currentDayPeriod.getOpeningDay().getOpeningHour(), offsetTime)) {
          return findDateTimeInPeriod(currentDayPeriod, offsetTime, currentDate);
        } else {
          OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
          LocalDate localDate = nextOpeningDay.getDate();

          if (nextOpeningDay.getAllDay()) {
            return toUtcDateTime(localDate, MIDNIGHT);
          } else {
            OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
            LocalTime startTime = openingHour.getStartTime();
            return toUtcDateTime(localDate, startTime);
          }
        }
      }
    } else {
      OpeningDay currentOpeningDay = currentDayPeriod.getOpeningDay();
      LocalDate currentDate = currentOpeningDay.getDate();

      if (currentOpeningDay.getOpen()) {
        if (currentOpeningDay.getAllDay()) {
          DateTime currentEndDateTime = currentDate.toDateTime(END_OF_A_DAY);
          DateTime offsetDateTime = currentDate.toDateTime(TEST_TIME_MORNING)
              .plusMinutes(duration);

          if (isInCurrentDateTime(currentEndDateTime, offsetDateTime)) {
            return new DateTime(offsetDateTime.toString()).withZoneRetainFields(UTC);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            LocalDate nextDate = nextOpeningDay.getDate();

            if (nextOpeningDay.getAllDay()) {
              return toUtcDateTime(nextDate, MIDNIGHT);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = openingHour.getStartTime();
              return toUtcDateTime(nextDate, startTime);
            }
          }
        } else {
          LocalTime offsetTime = TEST_TIME_MORNING.plusMinutes(duration);
          if (isInPeriodOpeningDay(currentOpeningDay.getOpeningHour(), offsetTime)) {
            return toUtcDateTime(currentDate, offsetTime);
          } else {
            OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
            LocalDate nextDate = nextOpeningDay.getDate();

            if (nextOpeningDay.getAllDay()) {
              return toUtcDateTime(nextDate, MIDNIGHT);
            } else {
              OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
              LocalTime startTime = openingHour.getStartTime();
              return toUtcDateTime(nextDate, startTime);
            }
          }
        }
      } else {
        OpeningDay nextOpeningDay = nextDayPeriod.getOpeningDay();
        LocalDate nextDate = nextOpeningDay.getDate();

        if (nextOpeningDay.getAllDay()) {
          return toUtcDateTime(nextDate, MIDNIGHT);
        }
        OpeningHour openingHour = nextOpeningDay.getOpeningHour().get(0);
        LocalTime startTime = openingHour.getStartTime();
        return toUtcDateTime(nextDate, startTime);
      }
    }
  }

  /**
   * Determine whether time is in any of the periods
   */
  private static boolean isInPeriodOpeningDay(List<OpeningHour> openingHoursList, LocalTime timeShift) {
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
    return date.toDateTime(END_OF_A_DAY, UTC);
  }

  private DateTime getStartDateTimeOpeningDay(OpeningDay openingDay) {
    boolean allDay = openingDay.getAllDay();
    LocalDate date = openingDay.getDate();

    if (allDay) {
      return toStartOfDayDateTime(date);
    } else {
      List<OpeningHour> openingHours = openingDay.getOpeningHour();

      if (openingHours.isEmpty()) {
        return toStartOfDayDateTime(date);
      }
      OpeningHour openingHour = openingHours.get(0);
      LocalTime localTime = openingHour.getStartTime();
      return toUtcDateTime(date, localTime);
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

  private JsonObject createLoanPolicy(String name, boolean loanable,
    String dueDateManagement, int duration, String intervalId) {

    return new LoanPolicyBuilder()
      .withName(name)
      .withDescription("LoanPolicy")
      .withLoanable(loanable)
      .rolling(Period.from(duration, intervalId))
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

  private JsonObject createFixedLoanPolicy(UUID fixedDueDateScheduleId,
    String dueDateManagement) {

    return new LoanPolicyBuilder()
      .withName("MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY: FIXED")
      .withDescription("New LoanPolicy")
      .fixed(fixedDueDateScheduleId)
      .withClosedLibraryDueDateManagement(dueDateManagement)
      .renewFromCurrentDueDate()
      .create();
  }

  private IndividualResource useLoanPolicy(JsonObject loanPolicyEntry) {
    IndividualResource loanPolicy = loanPoliciesFixture.create(loanPolicyEntry);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.activeNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    return loanPolicy;
  }

  private DateTime currentYearDateTime(int month, int day, int hour, int minute,
    int second, DateTimeZone zone) {

    return DateTime.now(zone)
      .withMonthOfYear(month)
      .withDayOfMonth(day)
      .withHourOfDay(hour)
      .withMinuteOfHour(minute)
      .withSecondOfMinute(second)
      .withMillisOfSecond(0);
  }

  private void useFixedPolicy(UUID fixedDueDateScheduleId,
    DueDateManagement dueDateManagement) {

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("MOVE_TO_THE_END_OF_THE_PREVIOUS_OPEN_DAY: FIXED")
      .withDescription("New LoanPolicy")
      .fixed(fixedDueDateScheduleId)
      .withClosedLibraryDueDateManagement(dueDateManagement.getValue())
      .renewFromCurrentDueDate();

    use(loanPolicy);
    loanPolicy.create();
  }
}
