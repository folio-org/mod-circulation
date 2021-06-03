package org.folio.circulation.resources.renewal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.policy.Period.days;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.spy;

import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class RegularRenewalTest {

  private static final String ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST =
    "items cannot be renewed when there is an active recall request";
  private static final String ITEM_IS_NOT_LOANABLE = "item is not loanable";
  private static final String ITEM_IS_AGED_TO_LOST = "item is Aged to lost";
  private static final String LOAN_IS_NOT_RENEWABLE = "loan is not renewable";
  private static final String ITEMS_CANNOT_BE_RENEWED_ACTIVE_PENDING_HOLD_REQUEST =
    "Items with this loan policy cannot be renewed when there is an active, pending hold request";
  private static final String ALTERNATIVE_RENEWAL_PERIOD_FOR_HOLDS_IS_SPECIFIED =
    "Item's loan policy has fixed profile but alternative renewal period for holds is specified";
  private static final String POLICY_HAS_FIXED_PROFILE_BUT_RENEWAL_PERIOD_IS_SPECIFIED =
    "Item's loan policy has fixed profile but renewal period is specified";
  private static final String LOAN_AT_MAXIMUM_RENEWAL_NUMBER = "loan at maximum renewal number";
  private static final String CANNOT_DETERMINE_WHEN_TO_RENEW_FROM =
    "cannot determine when to renew from";
  private static final String RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE =
    "renewal would not change the due date";

  @Test
  public void canRenewLoan() {
    final var rollingPeriod = days(10);
    final var currentDueDate = now(UTC);
    final var expectedDueDate = currentDueDate.plus(rollingPeriod.timePeriod());

    final var loanPolicy = new LoanPolicyBuilder().rolling(rollingPeriod)
      .renewFromCurrentDueDate().asDomainObject();
    final var loan = new LoanBuilder()
      .withCheckoutServicePointId(UUID.randomUUID())
      .asDomainObject().changeDueDate(currentDueDate)
      .withLoanPolicy(loanPolicy);

    final var resultCompletableFuture = renew(loan, new OverridingErrorHandler(null));

    assertThat(resultCompletableFuture.succeeded(), is(true));
    assertThat(resultCompletableFuture.value().getDueDate().getMillis(),
      is(expectedDueDate.getMillis()));
  }

  @Test
  public void cannotRenewWhenRecallRequestedAndPolicyNorLoanableAndItemLost() {
    final var recallRequest = new RequestBuilder().recall().asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withLoanable(false).withRenewable(false).asDomainObject();
    final var loan = new LoanBuilder().asDomainObject()
      .changeItemStatusForItemAndLoan(AGED_TO_LOST)
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, recallRequest, errorHandler);

    assertEquals(3, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_AGED_TO_LOST));
  }

  @Test
  public void cannotRenewWhenRecallRequested() {
    final var recallRequest = new RequestBuilder().recall().asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder().asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, recallRequest, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
  }

  @Test
  public void cannotRenewWhenItemIsNotLoanable() {
    final var loanPolicy = new LoanPolicyBuilder().withLoanable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
  }

  @Test
  public void cannotRenewWhenLoanIsNotRenewable() {
    final var loanPolicy = new LoanPolicyBuilder().withRenewable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
  }

  @Test
  public void cannotRenewWhenHoldRequestIsNotRenewable() {
    final var request = new RequestBuilder().hold().asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withHolds(null, false, null)
      .asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_ACTIVE_PENDING_HOLD_REQUEST));
  }

  @Test
  public void cannotRenewWhenHoldRequestedAndFixedPolicyHasAlternativeRenewPeriod() {
    final var request = new RequestBuilder().hold().asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withHolds(null, true, days(1))
      .asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ALTERNATIVE_RENEWAL_PERIOD_FOR_HOLDS_IS_SPECIFIED));
  }

  @Test
  public void cannotRenewWhenHoldRequestedAndFixedPolicyHasRenewPeriod() {
    final var request = new RequestBuilder().hold().asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .renewWith(days(10))
      .withHolds(null, true, null)
      .asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      POLICY_HAS_FIXED_PROFILE_BUT_RENEWAL_PERIOD_IS_SPECIFIED));
  }

  @Test
  @Parameters({
    "Declared lost",
    "Aged to lost",
    "Claimed returned",
  })
  public void cannotRenewItemsWithDisallowedStatuses(String itemStatus) {
    final var loanPolicy = new LoanPolicyBuilder().asDomainObject();
    final var loan = new LoanBuilder().asDomainObject()
      .withLoanPolicy(loanPolicy)
      .changeItemStatusForItemAndLoan(ItemStatus.from(itemStatus));

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, "item is " + itemStatus));
  }

  @Test
  public void cannotRenewLoanThatReachedRenewalLimit() {
    final var renewalLimit = 2;
    final var loanPolicy = new LoanPolicyBuilder()
      .withRenewable(true).limitedRenewals(renewalLimit).asDomainObject();
    final var loan = Loan.from(new JsonObject().put("renewalCount", renewalLimit + 1))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
  }

  @Test
  public void cannotRenewWhenDueDateCannotBeCalculated() {
    final var loanPolicy = new LoanPolicyBuilder().rolling(days(10))
      .withRenewFrom("INVALID_RENEW_FROM").asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, CANNOT_DETERMINE_WHEN_TO_RENEW_FROM));
  }

  @Test
  public void cannotRenewWhenPolicyDueDateIsEarlierThanCurrentDueDate() {
    final var rollingPeriod = days(11);
    final var loanPolicy = new LoanPolicyBuilder()
      .rolling(rollingPeriod)
      .renewFromSystemDate()
      .asDomainObject();

    final var loan = new LoanBuilder().asDomainObject()
      .changeDueDate(now(UTC).plusMinutes(rollingPeriod.toMinutes() * 2))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
  }

  @Test
  public void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotLoanable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .withLoanable(false).asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
  }

  @Test
  public void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotRenewable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .rolling(days(1)).notRenewable().asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
  }

  private Result<Loan> renew(Loan loan, Request topRequest,
    CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(singletonList(topRequest)));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, now())
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renew(Loan loan, CirculationErrorHandler errorHandler) {
    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, now())
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renew(LoanPolicy loanPolicy, Request topRequest,
    CirculationErrorHandler errorHandler) {

    final var loan = new LoanBuilder().asDomainObject().withLoanPolicy(loanPolicy);

    return renew(loan, topRequest, errorHandler);
  }

  private Result<Loan> renew(LoanPolicy loanPolicy,
    CirculationErrorHandler errorHandler) {

    final var loan = new LoanBuilder().asDomainObject().withLoanPolicy(loanPolicy);
    return renew(loan, errorHandler);
  }

  private boolean matchErrorReason(CirculationErrorHandler errorHandler, String expectedReason) {
    return errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .anyMatch(httpFailure -> httpFailure.hasErrorWithReason(expectedReason));
  }
}
