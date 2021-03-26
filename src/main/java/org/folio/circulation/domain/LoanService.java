package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.domain.policy.library.EndOfPreviousDayStrategy;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

public class LoanService {

  private final ClosedLibraryStrategyService closedLibraryStrategyService;

  public LoanService(Clients clients) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
      DateTime.now(DateTimeZone.UTC), false);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> truncateLoanWhenItemRecalled(
    LoanAndRelatedRecords records) {
    RequestQueue requestQueue = records.getRequestQueue();
    Collection<Request> requests = requestQueue.getRequests();

    if(requests.isEmpty()) {
      return completedFuture(succeeded(records));
    }

    if (!requestQueue.containsRequestOfType(RequestType.RECALL)) {
      return completedFuture(succeeded(records));
    }

    final Loan loanToRecall = records.getLoan();
    final LoanPolicy loanPolicy = loanToRecall.getLoanPolicy();

    if (loanToRecall.wasDueDateChangedByRecall()) {
      return completedFuture(succeeded(records));
    }

    return completedFuture(loanPolicy
      .recall(loanToRecall)
      .map(records::withLoan))
      .thenCompose(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> truncateLoanDueDateIfPatronExpiresEarlier(
    LoanAndRelatedRecords records) {

    Loan loan = records.getLoan();
    DateTime userExpirationDate = loan.getUser().getExpirationDate();

    if (userExpirationDate != null && userExpirationDate.isBefore(loan.getDueDate())) {
      return closedLibraryStrategyService.applyStrategyForExpiredPatron(records,
        new EndOfPreviousDayStrategy(records.getTimeZone()));
    }

    return Result.ofAsync(() -> records);
  }
}
