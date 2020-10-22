package org.folio.circulation.resources.renewal;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.folio.circulation.domain.ItemStatus.CLAIMED_RETURNED;
import static org.folio.circulation.domain.ItemStatus.DECLARED_LOST;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.resources.RenewalValidator.CAN_NOT_RENEW_ITEM_ERROR;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS;
import static org.folio.circulation.resources.RenewalValidator.errorForRecallRequest;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.folio.circulation.resources.RenewalValidator.itemByIdValidationError;
import static org.folio.circulation.resources.RenewalValidator.loanPolicyValidationError;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class RegularRenewalStrategy implements RenewalStrategy {
  private static final EnumSet<ItemStatus> ITEM_STATUSES_DISALLOWED_FOR_RENEW = EnumSet
    .of(DECLARED_LOST, CLAIMED_RETURNED, AGED_TO_LOST);

  @Override
  public CompletableFuture<Result<RenewalContext>> renew(RenewalContext context,
    Clients clients) {

    final ClosedLibraryStrategyService strategyService =
      ClosedLibraryStrategyService.using(clients, DateTime.now(DateTimeZone.UTC), true);

    return completedFuture(renew(context))
      .thenCompose(r -> r.after(strategyService::applyClosedLibraryDueDateManagement));
  }

  private Result<RenewalContext> renew(RenewalContext context) {
    Loan loan = context.getLoan();
    RequestQueue requestQueue = context.getRequestQueue();

    return renew(loan, DateTime.now(DateTimeZone.UTC), requestQueue)
      .map(context::withLoan);
  }

  public Result<Loan> renew(Loan loan, DateTime systemDate, RequestQueue requestQueue) {
    return refuseWhenRenewIsNotAllowed(loan, requestQueue)
      .next(notUsed -> calculateNewDueDate(loan, requestQueue, systemDate))
      .next(newDueDate -> errorWhenEarlierOrSameDueDate(loan, newDueDate))
      .map(dueDate ->  loan.renew(dueDate, loan.getLoanPolicy().getId()));
  }

  private Result<DateTime> calculateNewDueDate(Loan loan, RequestQueue requestQueue, DateTime systemDate) {
    final var loanPolicy = loan.getLoanPolicy();
    final var isRenewalWithHoldRequest = isHold(getFirstRequestInQueue(requestQueue));

    return loanPolicy.determineStrategy(null, true, isRenewalWithHoldRequest, systemDate)
        .calculateDueDate(loan);
  }

  private Result<Void> refuseWhenRenewIsNotAllowed(Loan loan, RequestQueue requestQueue) {
    final List<ValidationError> errors = new ArrayList<>();
    final LoanPolicy loanPolicy = loan.getLoanPolicy();
    final Request firstRequest = getFirstRequestInQueue(requestQueue);

    if (hasRecallRequest(firstRequest)) {
      errors.add(errorForRecallRequest(
        "items cannot be renewed when there is an active recall request",
        firstRequest.getId()));
    }

    if (loanPolicy.isNotLoanable()) {
      errors.add(loanPolicyValidationError(loanPolicy, "item is not loanable"));
    }

    if (loanPolicy.isNotRenewable()) {
      errors.add(loanPolicyValidationError(loanPolicy, "loan is not renewable"));
    }

    if (isHold(firstRequest)) {
      if (!loanPolicy.isHoldRequestRenewable()) {
        errors.add(loanPolicyValidationError(loanPolicy, CAN_NOT_RENEW_ITEM_ERROR));
      }

      if (loanPolicy.isFixed()) {
        if (loanPolicy.hasAlternateRenewalLoanPeriodForHolds()) {
          errors.add(loanPolicyValidationError(loanPolicy,
            FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS));
        }
        if (loanPolicy.hasRenewalPeriod()) {
          errors.add(loanPolicyValidationError(loanPolicy,
            FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD));
        }
      }
    }

    if (ITEM_STATUSES_DISALLOWED_FOR_RENEW.contains(loan.getItemStatus())) {
      errors.add(itemByIdValidationError("item is " + loan.getItemStatusName(),
        loan.getItemId()));
    }

    if (loanPolicy.hasReachedRenewalLimit(loan)) {
      errors.add(loanPolicyValidationError(loanPolicy, "loan at maximum renewal number"));
    }

    return errors.isEmpty() ? succeeded(null) : failedValidation(errors);
  }

  private Request getFirstRequestInQueue(RequestQueue requestQueue) {
    return requestQueue.getRequests().stream()
      .findFirst().orElse(null);
  }

  private boolean hasRecallRequest(Request firstRequest) {
    return firstRequest != null && firstRequest.getRequestType() == RECALL;
  }

  private boolean isHold(Request request) {
    return request != null && request.getRequestType() == HOLD;
  }
}
