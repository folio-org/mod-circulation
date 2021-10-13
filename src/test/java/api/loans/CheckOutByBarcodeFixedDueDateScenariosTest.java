package api.loans;

import static api.support.builders.FixedDueDateSchedule.wholeMonth;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY;
import static api.support.fixtures.CalendarExamples.CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTimeOptional;
import static org.folio.circulation.support.utils.DateTimeUtil.atEndOfDay;
import static org.folio.circulation.support.utils.DateTimeUtil.atStartOfDay;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.policy.DueDateManagement;
import org.folio.circulation.domain.policy.Period;
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

    ZonedDateTime loanDate =
      ZonedDateTime.of(2019, 1, 25, 10, 0, 0, 0, UTC);

    ZonedDateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, UTC);
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, 1, limitDueDate));
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

    ZonedDateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, UTC);

    assertThat("due date should be " + formatDateTimeOptional(expectedDate),
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  void shouldUseMoveToThePreviousOpenDayStrategyForFixedLoanPolicyWhenDueDateExtendsBeyondFixedDueDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    ZonedDateTime loanDate =
      ZonedDateTime.of(2019, 1, 25, 10, 0, 0, 0, UTC);

    ZonedDateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_CURR_DAY, UTC);

    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, 1, limitDueDate));
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

    ZonedDateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_PREV_DAY, UTC);

    assertThat("due date should be " + formatDateTimeOptional(expectedDate),
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }

  @Test
  void shouldUseSelectedClosedLibraryStrategyWhenDueDateDoesNotExtendBeyondFixedDueDate() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource jessica = usersFixture.jessica();
    UUID checkoutServicePointId = UUID.fromString(CASE_FRI_SAT_MON_SERVICE_POINT_ID);

    ZonedDateTime loanDate = ZonedDateTime.of(2019, 1, 25, 10, 0, 0, 0, UTC);

    ZonedDateTime limitDueDate =
      atStartOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, UTC);
    FixedDueDateSchedulesBuilder fixedDueDateSchedules = new FixedDueDateSchedulesBuilder()
      .withName("Fixed Due Date Schedule")
      .addSchedule(wholeMonth(2019, 1, limitDueDate));
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

    ZonedDateTime expectedDate =
      atEndOfDay(CASE_FRI_SAT_MON_SERVICE_POINT_NEXT_DAY, UTC);

    assertThat("due date should be " + formatDateTimeOptional(expectedDate),
      loan.getJson().getString("dueDate"), isEquivalentTo(expectedDate));
  }
}
