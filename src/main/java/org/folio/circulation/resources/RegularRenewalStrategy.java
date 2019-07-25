package org.folio.circulation.resources;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanAndRelatedRecords;
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

    return completedFuture(renew(relatedRecords))
      .thenCompose(r -> r.after(strategyService::applyClosedLibraryDueDateManagement));
  }

  private Result<LoanAndRelatedRecords> renew(LoanAndRelatedRecords relatedRecords) {
    Loan loan = relatedRecords.getLoan();
    LoanPolicy loanPolicy = loan.getLoanPolicy();
    RequestQueue requestQueue = relatedRecords.getRequestQueue();

    return loanPolicy.renew(loan, DateTime.now(DateTimeZone.UTC), requestQueue)
      .map(relatedRecords::withLoan);
  }
}
