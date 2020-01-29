package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestType.HOLD;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.resources.RenewalValidator.CAN_NOT_RENEW_ITEM_ERROR;
import static org.folio.circulation.resources.RenewalValidator.DECLARED_LOST_ITEM_RENEWED_ERROR;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD;
import static org.folio.circulation.resources.RenewalValidator.FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS;
import static org.folio.circulation.resources.RenewalValidator.errorForRecallRequest;
import static org.folio.circulation.resources.RenewalValidator.errorWhenEarlierOrSameDueDate;
import static org.folio.circulation.resources.RenewalValidator.itemByIdValidationError;
import static org.folio.circulation.resources.RenewalValidator.loanPolicyValidationError;
import static org.folio.circulation.support.Result.failed;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.CommonFailures.failedDueToServerError;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class RegularRenewalStrategy implements RenewalStrategy {

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> renew(
    LoanAndRelatedRecords relatedRecords, JsonObject requestBody, Clients clients) {

    final ClosedLibraryStrategyService strategyService =
      ClosedLibraryStrategyService.using(clients, DateTime.now(DateTimeZone.UTC), true);

    return completedFuture(renew(relatedRecords))
      .thenCompose(r -> r.after(strategyService::applyClosedLibraryDueDateManagement));
  }

  private Result<LoanAndRelatedRecords> renew(LoanAndRelatedRecords relatedRecords) {
    Loan loan = relatedRecords.getLoan();
    RequestQueue requestQueue = relatedRecords.getRequestQueue();

    return renew(loan, DateTime.now(DateTimeZone.UTC), requestQueue)
      .map(relatedRecords::withLoan);
  }

  public Result<Loan> renew(Loan loan, DateTime systemDate, RequestQueue requestQueue) {
    //TODO: Create HttpResult wrapper that traps exceptions
    try {
      List<ValidationError> errors = new ArrayList<>();
      LoanPolicy loanPolicy = loan.getLoanPolicy();

      Request firstRequest = requestQueue.getRequests().stream()
        .findFirst().orElse(null);

      if (hasRecallRequest(firstRequest)) {
        String reason = "items cannot be renewed when there is an active recall request";
        errors.add(errorForRecallRequest(reason, firstRequest.getId()));
      }

      if (loanPolicy.isNotLoanable()) {
        errors.add(loanPolicyValidationError(loanPolicy, "item is not loanable"));
        return failedValidation(errors);
      }
      if (loanPolicy.isNotRenewable()) {
        errors.add(loanPolicyValidationError(loanPolicy, "loan is not renewable"));
        return failedValidation(errors);
      }
      boolean isRenewalWithHoldRequest = false;
      //Here can be either Hold request or null only
      if (isHold(firstRequest)) {
        if (!loanPolicy.isHoldRequestRenewable()) {
          errors.add(loanPolicyValidationError(loanPolicy, CAN_NOT_RENEW_ITEM_ERROR));
          return failedValidation(errors);
        }

        if (loanPolicy.isFixed()) {
          if (loanPolicy.hasAlternateRenewalLoanPeriodForHolds()) {
            return failed(
              new ServerErrorFailure(FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD_FOR_HOLDS)
            );
          }
          if (loanPolicy.hasRenewalPeriod()) {
            return failed(
              new ServerErrorFailure(FIXED_POLICY_HAS_ALTERNATE_RENEWAL_PERIOD)
            );
          }
        }

        isRenewalWithHoldRequest = true;
      }
      if (loan.hasItemWithStatus(ItemStatus.DECLARED_LOST)) {
        errors.add(itemByIdValidationError(DECLARED_LOST_ITEM_RENEWED_ERROR,
          loan.getItemId()));
        return failedValidation(errors);
      }
      final Result<DateTime> proposedDueDateResult =
        loanPolicy.determineStrategy(null, true, isRenewalWithHoldRequest, systemDate)
          .calculateDueDate(loan);

      //TODO: Need a more elegent way of combining validation errors
      if(proposedDueDateResult.failed()) {
        if (proposedDueDateResult.cause() instanceof ValidationErrorFailure) {
          ValidationErrorFailure failureCause =
            (ValidationErrorFailure) proposedDueDateResult.cause();

          errors.addAll(failureCause.getErrors());
        }
      }
      else {
        errorWhenEarlierOrSameDueDate(loan, proposedDueDateResult.value(), errors);
      }

      loanPolicy.errorWhenReachedRenewalLimit(loan, errors);

      if (errors.isEmpty()) {
        return proposedDueDateResult.map(dueDate -> loan.renew(dueDate, loanPolicy.getId()));
      }
      else {
        return failedValidation(errors);
      }
    }
    catch(Exception e) {
      return failedDueToServerError(e);
    }
  }

  private boolean hasRecallRequest(Request firstRequest) {
    return firstRequest != null && firstRequest.getRequestType() == RECALL;
  }

  private boolean isHold(Request request) {
    return request != null && request.getRequestType() == HOLD;
  }
}
