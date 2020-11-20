package api.loans;

import static api.support.fixtures.CalendarExamples.CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID;
import static api.support.fixtures.CalendarExamples.CASE_ONE_DAY_IS_OPEN_NEXT_TWO_DAYS_CLOSED;
import static api.support.fixtures.CalendarExamples.FIRST_DAY_OPEN;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_UNPROCESSABLE_ENTITY;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.Period.months;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTimeZone.UTC;

import java.util.UUID;

import org.joda.time.DateTime;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

public class CheckOutToExpiringPatronTests extends APITests {
  @Test
  public void dueDateShouldBeTruncatedToTheEndOfLastWorkingDayBeforePatronExpiration() {
    mockClockManagerToReturnFixedDateTime(new DateTime(2020, 10, 27, 10, 0, UTC));
    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(
      createRulesWithFixedDueDateInLoanPolicy("m " + book));

    IndividualResource item = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve(user -> user.expires(
      DateTime.now().plusDays(3)));

    JsonObject response = checkOutFixture.checkOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(CASE_ONE_DAY_IS_OPEN_NEXT_TWO_DAYS_CLOSED)).getJson();

    mockClockManagerToReturnDefaultDateTime();
    assertThat(DateTime.parse(response.getString("dueDate")).toLocalDate(), is(FIRST_DAY_OPEN));
  }

  @Test
  public void dueDateTruncationForPatronExpirationFailsWhenNoCalendarIsDefinedForServicePoint() {
    mockClockManagerToReturnFixedDateTime(new DateTime(2020, 10, 27, 10, 0, UTC));

    final UUID book = materialTypesFixture.book().getId();

    circulationRulesFixture.updateCirculationRules(
      createRulesWithFixedDueDateInLoanPolicy("m " + book));

    IndividualResource item = itemsFixture.basedUponNod();
    IndividualResource steve = usersFixture.steve(user -> user.expires(
      DateTime.now().plusDays(3)));

    final var response = checkOutFixture.attemptCheckOutByBarcode(
      new CheckOutByBarcodeRequestBuilder()
        .forItem(item)
        .to(steve)
        .at(CASE_CALENDAR_IS_EMPTY_SERVICE_POINT_ID));

    mockClockManagerToReturnDefaultDateTime();

    assertThat(response, hasStatus(HTTP_UNPROCESSABLE_ENTITY));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Calendar timetable is absent for requested date"))));

    final var incorrectlyUpdateItem = itemsClient.get(item);

    // As the truncation of the due date happens after the item has been updated
    // the item is checked out in error
    assertThat(incorrectlyUpdateItem, hasItemStatus("Checked out"));
  }

  private IndividualResource prepareLoanPolicyWithItemLimitAndFixedDueDate(
    int itemLimit, UUID fixedDueDateScheduleId) {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy with item limit and fixed due date")
        .withItemLimit(itemLimit)
        .fixed(fixedDueDateScheduleId)
        .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue()));
  }

  private IndividualResource prepareLoanPolicyWithoutItemLimit() {
    return loanPoliciesFixture.create(
      new LoanPolicyBuilder()
        .withName("Loan Policy without item limit")
        .rolling(months(2))
        .renewFromCurrentDueDate());
  }

  private String createRulesWithFixedDueDateInLoanPolicy(String ruleCondition) {
    UUID fixedDueDateScheduleId = loanPoliciesFixture.createExampleFixedDueDateSchedule().getId();
    final String loanPolicyWithItemLimitAndFixedDueDateId = prepareLoanPolicyWithItemLimitAndFixedDueDate(
      1, fixedDueDateScheduleId).getId().toString();
    final String loanPolicyWithoutItemLimitId = prepareLoanPolicyWithoutItemLimit().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();
    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyOverdueFinePolicy = overdueFinePoliciesFixture.facultyStandard().getId().toString();
    final String anyLostItemFeePolicy = lostItemFeePoliciesFixture.facultyStandard().getId().toString();

    return String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy: l " + loanPolicyWithoutItemLimitId + " r " + anyRequestPolicy + " n " + anyNoticePolicy + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy,
      ruleCondition + " : l " + loanPolicyWithItemLimitAndFixedDueDateId + " r " + anyRequestPolicy + " n " + anyNoticePolicy  + " o " + anyOverdueFinePolicy + " i " + anyLostItemFeePolicy);
  }
}
