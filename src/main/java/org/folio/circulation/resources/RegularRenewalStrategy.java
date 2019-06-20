package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestType.RECALL;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class RegularRenewalStrategy implements RenewalStrategy {

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> renew(
    LoanAndRelatedRecords relatedRecords, JsonObject requestBody, Clients clients) {

    final ClosedLibraryStrategyService strategyService =
      ClosedLibraryStrategyService.using(clients, DateTime.now(DateTimeZone.UTC), true);

    return completedFuture(refuseWhenFirstRequestIsRecall(relatedRecords))
      .thenApply(r -> r.next(this::renew))
      .thenCompose(r -> r.after(strategyService::applyClosedLibraryDueDateManagement));
  }

  private Result<LoanAndRelatedRecords> refuseWhenFirstRequestIsRecall(LoanAndRelatedRecords relatedRecords) {
    final RequestQueue requestQueue = relatedRecords.getRequestQueue();
    Request firstRequest = requestQueue.getRequests().stream()
      .findFirst().orElse(null);

    if (firstRequest != null && firstRequest.getRequestType() == RECALL) {
      String reason = "items cannot be renewed when there is an active recall request";
      return failedValidation(reason, "request id", firstRequest.getId());
    }
    return succeeded(relatedRecords);
  }

  private Result<LoanAndRelatedRecords> renew(LoanAndRelatedRecords relatedRecords) {
    Loan loan = relatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();

    return loanPolicy.renew(loan, DateTime.now(DateTimeZone.UTC))
      .map(relatedRecords::withLoan);
  }
}
