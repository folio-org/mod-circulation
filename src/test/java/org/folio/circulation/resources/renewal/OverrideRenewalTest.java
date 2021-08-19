package org.folio.circulation.resources.renewal;

import static api.support.matchers.JsonObjectMatcher.hasNoJsonPath;
import static api.support.matchers.ResultMatchers.hasValidationError;
import static api.support.matchers.ResultMatchers.succeeded;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasMessageContaining;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.policy.Period.weeks;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.json.JsonPropertyWriter.writeByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Seconds.seconds;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

public class OverrideRenewalTest {
  private static final String NEW_DUE_DATE_IS_REQUIRED_ERROR =
    "New due date is required when renewal would not change the due date";
  private static final String OVERRIDE_DUE_DATE_MUST_BE_SPECIFIED_ERROR =
    "New due date must be specified when due date calculation fails";

  @Test
  public void shouldUseOverrideDateWhenLoanIsNotLoanable() {
    final DateTime overrideDate = now().plusMonths(1);
    final JsonObject loanPolicyJson = new LoanPolicyBuilder()
      .withLoanable(false)
      .create();

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), overrideDate);

    assertDueDate(overrideDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void shouldUseOverrideDateWhenLoanIsNotRenewable() {
    final DateTime overrideDate = now().plusMonths(1);
    final JsonObject loanPolicyJson = new LoanPolicyBuilder()
      .notRenewable()
      .create();

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), overrideDate);

    assertDueDate(overrideDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenLoanIsNotRenewable() {
    final JsonObject loanPolicyJson = new LoanPolicyBuilder()
      .notRenewable()
      .create();

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), null);

    assertThat(renewedLoan, hasValidationError(hasMessage(OVERRIDE_DUE_DATE_MUST_BE_SPECIFIED_ERROR)));
  }

  @Test
  public void overrideDateIsRequiredWhenLoanIsNotLoanable() {
    final JsonObject loanPolicyJson = new LoanPolicyBuilder()
      .withLoanable(false)
      .create();

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), null);

    assertThat(renewedLoan, hasValidationError(hasMessage(OVERRIDE_DUE_DATE_MUST_BE_SPECIFIED_ERROR)));
  }

  @Test
  public void shouldUseOverrideDateWhenUnableToCalculateCalculatedDueDate() {
    final DateTime overrideDate = now().plusMonths(1);
    final JsonObject loanPolicyJson = rollingPolicy().create();

    // Use undefined strategy to break due date calculation
    writeByPath(loanPolicyJson, "UNDEFINED_STRATEGY", "renewalsPolicy", "renewFromId");

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), overrideDate);

    assertDueDate(overrideDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenUnableToCalculateCalculatedDueDate() {
    final JsonObject loanPolicyJson = rollingPolicy().create();

    writeByPath(loanPolicyJson, "UNDEFINED_STRATEGY", "renewalsPolicy", "renewFromId");

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), null);

    assertThat(renewedLoan, hasValidationError(hasMessage(OVERRIDE_DUE_DATE_MUST_BE_SPECIFIED_ERROR)));
  }

  @Test
  public void shouldUseOverrideDateWhenReachedNumberOfRenewalsAndNewDueDateBeforeCurrent() {
    final DateTime overrideDueDate = now(UTC).plusWeeks(2);
    final LoanPolicy loanPolicy = LoanPolicy.from(rollingPolicy()
      .limitedRenewals(1)
      .create());

    final Loan loan = Loan.from(new JsonObject().put("renewalCount", 2))
      .changeDueDate(now(UTC).plusWeeks(1).plusSeconds(1))
      .withItem(createCheckedOutItem())
      .withLoanPolicy(loanPolicy);

    final Result<Loan> renewedLoan = renew(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void shouldUseCalculatedDueDateWhenReachedNumberOfRenewalsAndNewDueDateAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final LoanPolicy loanPolicy = LoanPolicy.from(rollingPolicy()
      .limitedRenewals(1)
      .create());

    final Loan loan = Loan.from(new JsonObject().put("renewalCount", 2))
      .withItem(createCheckedOutItem())
      .withLoanPolicy(loanPolicy);

    final Result<Loan> renewedLoan = renew(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenReachedNumberOfRenewalsAndNewDueDateBeforeCurrent() {
    final LoanPolicy loanPolicy = LoanPolicy.from(rollingPolicy()
      .limitedRenewals(1).create());

    final Loan loan = Loan.from(new JsonObject().put("renewalCount", 2))
      .changeDueDate(now(UTC).plusDays(8))
      .withLoanPolicy(loanPolicy);

    final Result<Loan> renewedLoan = renew(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void shouldUseOverrideDateWhenRecallRequestedAndNewDateIsBeforeCurrent() {
    final DateTime overrideDueDate = now(UTC).plusDays(9);
    final Loan loan = createLoanWithDueDateAfterCalculated();

    final Result<Loan> renewedLoan = renewWithRecall(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenRecallRequestedAndNewDateIsBeforeCurrent() {
    final Loan loan = createLoanWithDueDateAfterCalculated();

    final Result<Loan> renewedLoan = renewWithRecall(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void shouldUseCalculatedDateWhenRecallRequestedAndNewDateIsAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final Loan loan = createLoanWithDefaultPolicy();

    final Result<Loan> renewedLoan = renewWithRecall(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void shouldUseOverrideDateWhenItemLostAndNewDateIsBeforeCurrent() {
    final DateTime overrideDueDate = now(UTC).plusDays(9);

    final Loan loan = createLoanWithDueDateAfterCalculated()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenItemLostAndNewDateIsBeforeCurrent() {
    final Loan loan = createLoanWithDueDateAfterCalculated()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void shouldUseCalculatedDateWhenItemLostAndNewDateIsAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final Loan loan = createLoanWithDefaultPolicy()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void canOverrideLoanWhenCurrentDueDateIsAfterCalculated() {
    final DateTime overrideDate = now(UTC).plusDays(9);
    final Loan loan = createLoanWithDueDateAfterCalculated();

    final Result<Loan> renewedLoan = renew(loan, overrideDate);

    assertDueDate(overrideDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenCurrentDueDateIsAfterCalculated() {
    final Loan loan = createLoanWithDueDateAfterCalculated();

    final Result<Loan> renewedLoan = renew(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void cannotOverrideWhenCurrentDueDateIsBeforeCalculated() {
    final Loan loan = createLoanWithDefaultPolicy();

    final Result<Loan> renewedLoan = renew(loan, now(UTC).plusDays(9));

    assertThat(renewedLoan, hasValidationError(
      hasMessageContaining("Override renewal does not match any of expected cases")));
  }

  @Test
  public void cannotOverrideWhenOverrideDateBeforeCurrentDueDate() {
    final DateTime overrideDate = now(UTC).plusWeeks(1).plusSeconds(1);
    final Loan loan = createLoanWithDueDateAfterCalculated();

    final Result<Loan> renewedLoan = renew(loan, overrideDate);

    assertThat(renewedLoan, hasValidationError(hasMessage("renewal would not change the due date")));
  }

  @Test
  public void nonLoanableAgedToLostItemShouldBeProperlyRenewed() {
    final DateTime newDueDate = now(UTC).plusWeeks(1);
    final DateTime ageToLostDate = now(UTC);

    final LoanPolicy loanPolicy = LoanPolicy.from(new LoanPolicyBuilder()
      .withLoanable(false)
      .create());

    final Loan loan = Loan.from(new JsonObject())
      .withItem(createCheckedOutItem())
      .withLoanPolicy(loanPolicy);

    loan.ageOverdueItemToLost(ageToLostDate)
      .setAgedToLostDelayedBilling(false, ageToLostDate.plusDays(1));

    final Result<Loan> renewedLoan = renew(loan, newDueDate);

    assertDueDate(newDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());

    assertThat(renewedLoan.value().asJson(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }

  private LoanPolicyBuilder rollingPolicy() {
    return new LoanPolicyBuilder()
      .rolling(weeks(2))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .renewWith(weeks(1));
  }

  private Result<Loan> renew(LoanPolicy loanPolicy, DateTime overrideDueDate) {
    final Loan loan = Loan.from(new JsonObject())
      .withItem(createCheckedOutItem())
      .withLoanPolicy(loanPolicy);

    return renew(loan, overrideDueDate);
  }

  private Result<Loan> renew(Loan loan, DateTime overrideDueDate) {
    final JsonObject overrideRequest = createOverrideRequest(overrideDueDate);

    final RenewalContext renewalContext = RenewalContext.create(loan, overrideRequest, "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    return renew(renewalContext);
  }

  private Result<Loan> renew(RenewalContext context) {
    return new RenewByBarcodeResource(null).renewThroughOverride(context)
      .getNow(Result.failed(new ServerErrorFailure("Failure")))
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renewWithRecall(Loan loan, DateTime overrideDueDate) {
    final JsonObject overrideRequest = createOverrideRequest(overrideDueDate);

    final RenewalContext renewalContext = RenewalContext.create(loan, overrideRequest, "no-user")
      .withRequestQueue(new RequestQueue(singleton(createRecallRequest())));

    return renew(renewalContext);
  }

  private Request createRecallRequest() {
    return Request.from(new JsonObject().put("requestType", "Recall"));
  }

  private JsonObject createOverrideRequest(DateTime dueDate) {
    final JsonObject json = new JsonObject();
    final JsonObject overrideBlocks = new JsonObject();
    final JsonObject renewalBlock = new JsonObject();
    write(overrideBlocks, "comment", "A comment");
    write(renewalBlock, "dueDate", dueDate);
    write(overrideBlocks, "renewalDueDateRequiredBlock", renewalBlock);
    write(json, "overrideBlocks", overrideBlocks);

    return json;
  }

  private Item createItemWithStatus(String status) {
    final JsonObject json = new JsonObject();

    writeByPath(json, status, "status", "name");

    return Item.from(json);
  }

  private Item createDeclaredLostItem() {
    return createItemWithStatus("Declared lost");
  }

  private Item createCheckedOutItem() {
    return createItemWithStatus("Checked out");
  }

  private Loan createLoanWithDueDateAfterCalculated() {
    return createLoanWithDefaultPolicy()
      .changeDueDate(now(UTC).plusDays(8));
  }

  private Loan createLoanWithDefaultPolicy() {
    return Loan.from(new JsonObject())
      .withItem(createCheckedOutItem())
      .withLoanPolicy(LoanPolicy.from(rollingPolicy().create()));
  }

  private void assertDueDate(DateTime expected, Result<Loan> actual) {
    assertThat(actual, succeeded());
    assertEquals(expected.getMillis(), actual.value().getDueDate().getMillis());
  }

  private void assertDueDateWithinOneSecondAfter(DateTime after, Result<Loan> actual) {
    assertThat(actual, succeeded());
    assertThat(actual.value().getDueDate().toString(), withinSecondsAfter(seconds(1), after));
  }
}
