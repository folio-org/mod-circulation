package org.folio.circulation.resources.renewal;

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
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.joda.time.Seconds.seconds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.LoanPolicyBuilder;
import io.vertx.core.json.JsonObject;

public class OverrideRenewalStrategyTest {
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
  }

  @Test
  public void shouldUseOverrideDateWhenLoanIsNotRenewable() {
    final DateTime overrideDate = now().plusMonths(1);
    final JsonObject loanPolicyJson = new LoanPolicyBuilder()
      .notRenewable()
      .create();

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), overrideDate);

    assertDueDate(overrideDate, renewedLoan);
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
  public void shouldUseOverrideDateWhenUnableToCalculateProposedDueDate() {
    final DateTime overrideDate = now().plusMonths(1);
    final JsonObject loanPolicyJson = rollingPolicy().create();

    // Use undefined strategy to break due date calculation
    writeByPath(loanPolicyJson, "UNDEFINED_STRATEGY", "renewalsPolicy", "renewFromId");

    final Result<Loan> renewedLoan = renew(LoanPolicy.from(loanPolicyJson), overrideDate);

    assertDueDate(overrideDate, renewedLoan);
  }

  @Test
  public void overrideDateIsRequiredWhenUnableToCalculateProposedDueDate() {
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
      .withLoanPolicy(loanPolicy);

    final Result<Loan> renewedLoan = renew(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
  }

  @Test
  public void shouldUseProposedDueDateWhenReachedNumberOfRenewalsAndNewDueDateAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final LoanPolicy loanPolicy = LoanPolicy.from(rollingPolicy()
      .limitedRenewals(1)
      .create());

    final Loan loan = Loan.from(new JsonObject().put("renewalCount", 2))
      .withLoanPolicy(loanPolicy);

    final Result<Loan> renewedLoan = renew(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
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
    final Loan loan = createLoanWithDueDateAfterProposed();

    final Result<Loan> renewedLoan = renewWithRecall(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
  }

  @Test
  public void overrideDateIsRequiredWhenRecallRequestedAndNewDateIsBeforeCurrent() {
    final Loan loan = createLoanWithDueDateAfterProposed();

    final Result<Loan> renewedLoan = renewWithRecall(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void shouldUseProposedDateWhenRecallRequestedAndNewDateIsAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final Loan loan = createLoanWithDefaultPolicy();

    final Result<Loan> renewedLoan = renewWithRecall(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
  }

  @Test
  public void shouldUseOverrideDateWhenItemLostAndNewDateIsBeforeCurrent() {
    final DateTime overrideDueDate = now(UTC).plusDays(9);

    final Loan loan = createLoanWithDueDateAfterProposed()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, overrideDueDate);

    assertDueDate(overrideDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void overrideDateIsRequiredWhenItemLostAndNewDateIsBeforeCurrent() {
    final Loan loan = createLoanWithDueDateAfterProposed()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void shouldUseProposedDateWhenItemLostAndNewDateIsAfterCurrent() {
    final DateTime estimatedDueDate = now(UTC).plusWeeks(1);
    final Loan loan = createLoanWithDefaultPolicy()
      .withItem(createDeclaredLostItem());

    final Result<Loan> renewedLoan = renew(loan, null);

    assertDueDateWithinOneSecondAfter(estimatedDueDate, renewedLoan);
    assertEquals(CHECKED_OUT, renewedLoan.value().getItem().getStatus());
  }

  @Test
  public void canOverrideLoanWhenCurrentDueDateIsAfterProposed() {
    final DateTime overrideDate = now(UTC).plusDays(9);
    final Loan loan = createLoanWithDueDateAfterProposed();

    final Result<Loan> renewedLoan = renew(loan, overrideDate);

    assertDueDate(overrideDate, renewedLoan);
  }

  @Test
  public void overrideDateIsRequiredWhenCurrentDueDateIsAfterProposed() {
    final Loan loan = createLoanWithDueDateAfterProposed();

    final Result<Loan> renewedLoan = renew(loan, null);

    assertThat(renewedLoan, hasValidationError(hasMessage(NEW_DUE_DATE_IS_REQUIRED_ERROR)));
  }

  @Test
  public void cannotOverrideWhenCurrentDueDateIsBeforeProposed() {
    final Loan loan = createLoanWithDefaultPolicy();

    final Result<Loan> renewedLoan = renew(loan, now(UTC).plusDays(9));

    assertThat(renewedLoan, hasValidationError(
      hasMessageContaining("Override renewal does not match any of expected cases")));
  }

  @Test
  public void cannotOverrideWhenOverrideDateBeforeCurrentDueDate() {
    final DateTime overrideDate = now(UTC).plusWeeks(1).plusSeconds(1);
    final Loan loan = createLoanWithDueDateAfterProposed();

    final Result<Loan> renewedLoan = renew(loan, overrideDate);

    assertThat(renewedLoan, hasValidationError(hasMessage("renewal would not change the due date")));
  }

  private LoanPolicyBuilder rollingPolicy() {
    return new LoanPolicyBuilder()
      .rolling(weeks(2))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .renewWith(weeks(1));
  }

  private Result<Loan> renew(LoanPolicy loanPolicy, DateTime overrideDueDate) {
    final Loan loan = Loan.from(new JsonObject()).withLoanPolicy(loanPolicy);

    return renew(loan, overrideDueDate);
  }

  private Result<Loan> renew(Loan loan, DateTime overrideDueDate) {
    final JsonObject overrideRequest = createOverrideRequest(overrideDueDate);

    final RenewalContext renewalContext = RenewalContext.create(loan, overrideRequest, "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    return renew(renewalContext);
  }

  private Result<Loan> renew(RenewalContext context) {
    return new OverrideRenewalStrategy().renew(context, null)
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

    write(json, "comment", "A comment");
    write(json, "dueDate", dueDate);

    return json;
  }

  private Item createDeclaredLostItem() {
    final JsonObject json = new JsonObject();

    writeByPath(json, "Declared lost", "status", "name");

    return Item.from(json);
  }

  private Loan createLoanWithDueDateAfterProposed() {
    return createLoanWithDefaultPolicy()
      .changeDueDate(now(UTC).plusDays(8));
  }

  private Loan createLoanWithDefaultPolicy() {
    final Item checkedOutItem = Item.from(new JsonObject()
      .put("status", new JsonObject().put("name", "Checked out")));

    return Loan.from(new JsonObject())
      .withItem(checkedOutItem)
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
