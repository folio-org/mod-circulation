package api.loans;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import java.util.UUID;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeConstants;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.FixedDueDateSchedulesBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;

/**
 * Test cases for scenarios when due date calculated by CLDDM
 * extends beyond the DueDate for the configured Fixed due date schedule
 */
class CheckOutByBarcodeFixedDueDateScenariosTest extends APITests {

  @Test
  void shouldUseMoveToThePreviousOpenDayStrategyForLongTermLoanPolicyWhenDueDateExtendsBeyondFixedDueDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, UTC);

    DateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, UTC);
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate));
    UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(8))
      .withClosedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue())
      .limitedBySchedule(fixedDueDateSchedulesId);

    use(loanPolicy);

    IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, UTC);

    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  void shouldUseMoveToThePreviousOpenDayStrategyForFixedLoanPolicyWhenDueDateExtendsBeyondFixedDueDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, UTC);

    DateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, UTC);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate));
    UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .withClosedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue())
      .fixed(fixedDueDateSchedulesId);

    use(loanPolicy);

    IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, UTC);

    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  void shouldUseSelectedClosedLibraryStrategyWhenDueDateDoesNotExtendBeyondFixedDueDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    DateTime loanDate =
      new DateTime(2019, DateTimeConstants.JANUARY, 25, 10, 0, UTC);

    DateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, UTC);
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, DateTimeConstants.JANUARY, limitDueDate));
    UUID fixedDueDateSchedulesId = loanPoliciesFixture.createSchedule(
      fixedDueDateSchedules).getId();

    LoanPolicyBuilder loanPolicy = new LoanPolicyBuilder()
      .withName("Loan policy")
      .rolling(Period.days(8))
      .withClosedLibraryDueDateManagement(DueDateManagement.MOVE_TO_THE_END_OF_THE_NEXT_OPEN_DAY.getValue())
      .limitedBySchedule(fixedDueDateSchedulesId);

    use(loanPolicy);

    IndividualResource loan = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .to(jessica)
        .at(checkoutServicePointId)
        .on(loanDate));

    DateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, UTC);

    assertThat("due date should be " + expectedDate,
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }
}
