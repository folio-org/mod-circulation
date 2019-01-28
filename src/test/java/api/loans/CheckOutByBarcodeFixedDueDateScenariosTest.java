package api.loans;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.joda.time.format.DateTimeFormat;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY;
import static api.support.fixtures.CalendarExamples.END_TIME_SECOND_PERIOD;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.resources.CheckOutByBarcodeResource.DATE_TIME_FORMATTER;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test cases for scenarios when due date calculated by CLDDM
 * extends beyond the DueDate for the configured Fixed due date schedule
 */
public class CheckOutByBarcodeFixedDueDateScenariosTest extends APITests {

  @Test
  public void shouldUseMoveToThePreviousOpenDayStrategyForLongTermLoanPolicyWhenDueDateExtendsBeyondFixedDueDate()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    DateTime limitDueDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withZoneRetainFields(DateTimeZone.UTC);
    UUID fixedDueDateScheduleId = fixedDueDateScheduleClient.create(new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate))).getId();
    schedulesToDelete.add(fixedDueDateScheduleId);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(8))
      .closedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY)
      .limitedBySchedule(fixedDueDateScheduleId);

    UUID loanPolicyId = loanPolicyClient.create(loanPolicy).getId();
    useLoanPolicyAsFallback(loanPolicyId);

    IndividualResource loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withTime(LocalTime.parse(END_TIME_SECOND_PERIOD))
        .withZoneRetainFields(DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void shouldUseMoveToThePreviousOpenDayStrategyForFixedLoanPolicyWhenDueDateExtendsBeyondFixedDueDate()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    DateTime limitDueDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withZoneRetainFields(DateTimeZone.UTC);
    UUID fixedDueDateScheduleId = fixedDueDateScheduleClient.create(new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate))).getId();
    schedulesToDelete.add(fixedDueDateScheduleId);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .closedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY)
      .fixed(fixedDueDateScheduleId);


    UUID loanPolicyId = loanPolicyClient.create(loanPolicy).getId();
    useLoanPolicyAsFallback(loanPolicyId);

    IndividualResource loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withTime(LocalTime.parse(END_TIME_SECOND_PERIOD))
        .withZoneRetainFields(DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }


  @Test
  public void shouldUseMoveToThePreviousOpenDayStrategyForLongTermLoanPolicyWhenDueDateDoesNotExtendBeyondFixedDueDate()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, DateTimeZone.UTC);

    DateTime limitDueDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withZoneRetainFields(DateTimeZone.UTC);
    UUID fixedDueDateScheduleId = fixedDueDateScheduleClient.create(new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate))).getId();
    schedulesToDelete.add(fixedDueDateScheduleId);

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(8))
      .closedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY)
      .limitedBySchedule(fixedDueDateScheduleId);

    UUID loanPolicyId = loanPolicyClient.create(loanPolicy).getId();
    useLoanPolicyAsFallback(loanPolicyId);

    IndividualResource loan = loansFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      DateTime.parse(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, DateTimeFormat.forPattern(DATE_TIME_FORMATTER))
        .withTime(LocalTime.parse(END_TIME_SECOND_PERIOD))
        .withZoneRetainFields(DateTimeZone.UTC);
    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  public void shouldUseMoveToTheEndOfTheCurrentServicePointHoursStrategyForLongTermLoanPolicyWhenDueDateExtendsBeyondFixedDueDate()
    throws
    InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    //implement after END_OF_THE_CURRENT_SERVICE_POINT_HOURS gets working correctly
  }
}
