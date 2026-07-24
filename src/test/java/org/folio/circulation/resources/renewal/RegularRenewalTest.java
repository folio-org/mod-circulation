package org.folio.circulation.resources.renewal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.policy.Period.days;
import static org.folio.circulation.resources.handlers.error.CirculationErrorType.RENEWAL_ITEM_IS_NOT_LOANABLE;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.spy;

import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.resources.handlers.error.CirculationErrorHandler;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.resources.handlers.error.OverridingErrorHandler;
import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.junit.jupiter.MockitoExtension;

import api.support.builders.LoanBuilder;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

@ExtendWith(MockitoExtension.class)
class RegularRenewalTest {

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

  private static final UUID ITEM_ID = UUID.randomUUID();

  @Test
  void canRenewLoan() {
    final var rollingPeriod = days(10);
    final var currentDueDate = getZonedDateTime();
    final var expectedDueDate = rollingPeriod.plusDate(currentDueDate);

    final var loanPolicy = new LoanPolicyBuilder().rolling(rollingPeriod)
      .renewFromCurrentDueDate().asDomainObject();
    final var loan = new LoanBuilder()
      .withCheckoutServicePointId(UUID.randomUUID())
      .asDomainObject().changeDueDate(currentDueDate)
      .withLoanPolicy(loanPolicy);

    final var resultCompletableFuture = renew(loan, new OverridingErrorHandler(null));

    assertThat(resultCompletableFuture.succeeded(), is(true));
    assertThat(resultCompletableFuture.value().getDueDate(), is(expectedDueDate));
  }

  @Test
  void cannotRenewWhenRecallRequestedAndPolicyNorLoanableAndItemLost() {
    final var recallRequest = new RequestBuilder().recall().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withLoanable(false).withRenewable(false).asDomainObject();
    final var loan = new LoanBuilder().withItemId(ITEM_ID).asDomainObject()
      .changeItemStatusForItemAndLoan(AGED_TO_LOST)
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, recallRequest, errorHandler);

    assertEquals(3, errorHandler.getErrors().size());
    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_AGED_TO_LOST));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.RENEWAL_BLOCKED_BY_RECALL));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.ITEM_NOT_LOANABLE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.ITEM_AGED_TO_LOST_NOT_RENEWABLE));
  }

  @Test
  void cannotRenewWhenRecallRequested() {
    final var recallRequest = new RequestBuilder().recall().withItemId(ITEM_ID).asDomainObject();
    final var loan = new LoanBuilder().withItemId(ITEM_ID).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, recallRequest, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_WHEN_THERE_IS_AN_ACTIVE_RECALL_REQUEST));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.RENEWAL_BLOCKED_BY_RECALL));
  }

  @Test
  void cannotRenewWhenItemIsNotLoanable() {
    final var loanPolicy = new LoanPolicyBuilder().withLoanable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorType(errorHandler, RENEWAL_ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.ITEM_NOT_LOANABLE));
  }

  @Test
  void cannotRenewWhenLoanIsNotRenewable() {
    final var loanPolicy = new LoanPolicyBuilder().withRenewable(false).asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.LOAN_NOT_RENEWABLE));
  }

  @Test
  void cannotRenewWhenHoldRequestIsNotRenewable() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .withHolds(null, false, null)
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ITEMS_CANNOT_BE_RENEWED_ACTIVE_PENDING_HOLD_REQUEST));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.RENEWAL_BLOCKED_BY_HOLD_REQUEST));
  }

  @Test
  void cannotRenewWhenHoldRequestedAndFixedPolicyHasAlternativeRenewPeriod() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .withHolds(null, true, days(1))
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      ALTERNATIVE_RENEWAL_PERIOD_FOR_HOLDS_IS_SPECIFIED));
    assertTrue(matchErrorCode(errorHandler,
      ErrorCode.FIXED_LOAN_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS));
  }

  @Test
  void cannotRenewWhenHoldRequestedAndFixedPolicyHasRenewPeriod() {
    final var request = new RequestBuilder().hold().withItemId(ITEM_ID).asDomainObject();
    final var loanPolicy = new LoanPolicyBuilder()
      .fixed(UUID.randomUUID())
      .renewWith(days(10))
      .withHolds(null, true, null)
      .asDomainObject();

    final var loan = new LoanBuilder()
      .withItemId(ITEM_ID)
      .asDomainObject()
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, request, errorHandler);

    assertTrue(matchErrorReason(errorHandler,
      POLICY_HAS_FIXED_PROFILE_BUT_RENEWAL_PERIOD_IS_SPECIFIED));
    assertTrue(matchErrorCode(errorHandler,
      ErrorCode.FIXED_LOAN_POLICY_HAS_RENEWAL_PERIOD));
  }

  @ParameterizedTest
  @CsvSource({
    "Declared lost, ITEM_DECLARED_LOST_NOT_RENEWABLE",
    "Aged to lost, ITEM_AGED_TO_LOST_NOT_RENEWABLE",
    "Claimed returned, ITEM_CLAIMED_RETURNED_NOT_RENEWABLE",
  })
  void cannotRenewItemsWithDisallowedStatuses(String itemStatus, ErrorCode errorCode) {
    final var loanPolicy = new LoanPolicyBuilder().asDomainObject();
    final var loan = new LoanBuilder().asDomainObject()
      .withLoanPolicy(loanPolicy)
      .changeItemStatusForItemAndLoan(ItemStatus.from(itemStatus));

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, "item is " + itemStatus));
    assertTrue(matchErrorCode(errorHandler, errorCode));
  }

  @Test
  void cannotRenewLoanThatReachedRenewalLimit() {
    final var renewalLimit = 2;
    final var loanPolicy = new LoanPolicyBuilder()
      .withRenewable(true).limitedRenewals(renewalLimit).asDomainObject();
    final var loan = Loan.from(new JsonObject().put("renewalCount", renewalLimit + 1))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_AT_MAXIMUM_RENEWAL_NUMBER));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.LOAN_RENEWAL_LIMIT_REACHED));
  }

  @Test
  void cannotRenewWhenDueDateCannotBeCalculated() {
    final var loanPolicy = new LoanPolicyBuilder().rolling(days(10))
      .withRenewFrom("INVALID_RENEW_FROM").asDomainObject();

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, CANNOT_DETERMINE_WHEN_TO_RENEW_FROM));
    assertTrue(matchErrorCode(errorHandler,
      ErrorCode.LOAN_POLICY_RENEW_FROM_NOT_RECOGNIZED));
  }

  @Test
  void cannotRenewWhenPolicyDueDateIsEarlierThanCurrentDueDate() {
    final var rollingPeriod = days(11);
    final var loanPolicy = new LoanPolicyBuilder()
      .rolling(rollingPeriod)
      .renewFromSystemDate()
      .asDomainObject();

    final var loan = new LoanBuilder().asDomainObject()
      .changeDueDate(getZonedDateTime().plusMinutes(rollingPeriod.toMinutes() * 2))
      .withLoanPolicy(loanPolicy);

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loan, errorHandler);

    assertTrue(matchErrorReason(errorHandler, RENEWAL_WOULD_NOT_CHANGE_THE_DUE_DATE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.RENEWAL_WOULD_NOT_CHANGE_DUE_DATE));
  }

  @Test
  void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotLoanable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .withLoanable(false).asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertEquals(1, errorHandler.getErrors().size());
    assertTrue(matchErrorType(errorHandler, RENEWAL_ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorReason(errorHandler, ITEM_IS_NOT_LOANABLE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.ITEM_NOT_LOANABLE));
  }

  @Test
  void shouldNotAttemptToCalculateDueDateWhenPolicyIsNotRenewable() {
    final var loanPolicy = spy(new LoanPolicyBuilder()
      .rolling(days(1)).notRenewable().asDomainObject());

    CirculationErrorHandler errorHandler = new OverridingErrorHandler(null);
    renew(loanPolicy, errorHandler);

    assertTrue(matchErrorReason(errorHandler, LOAN_IS_NOT_RENEWABLE));
    assertTrue(matchErrorCode(errorHandler, ErrorCode.LOAN_NOT_RENEWABLE));
  }

  private Result<Loan> renew(Loan loan, Request topRequest,
    CirculationErrorHandler errorHandler) {

    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(singletonList(topRequest)));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, getZonedDateTime())
      .map(RenewalContext::getLoan);
  }

  private Result<Loan> renew(Loan loan, CirculationErrorHandler errorHandler) {
    RenewalContext renewalContext = RenewalContext.create(loan, new JsonObject(), "no-user")
      .withRequestQueue(new RequestQueue(emptyList()));

    return new RenewByBarcodeResource(null)
      .regularRenew(renewalContext, errorHandler, getZonedDateTime())
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

  private boolean matchErrorCode(CirculationErrorHandler errorHandler, ErrorCode expectedCode) {
    return errorHandler.getErrors().keySet().stream()
      .map(ValidationErrorFailure.class::cast)
      .flatMap(httpFailure -> httpFailure.getErrors().stream())
      .anyMatch(error -> error.getCode() == expectedCode);
  }

  private boolean matchErrorType(CirculationErrorHandler errorHandler,
    CirculationErrorType errorType) {

    return errorHandler.getErrors().containsValue(errorType);
  }
}
