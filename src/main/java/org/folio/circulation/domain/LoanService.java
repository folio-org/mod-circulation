package org.folio.circulation.domain;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

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
    /*
      This gets the top request, since UpdateRequestQueue.java#L106 updates the request queue prior to loan creation.
      If that sequence changes, the following will need to be updated to requests.stream().skip(1).findFirst().orElse(null)
      and the condition above could do a > 1 comparison. (CIRC-277)
    */
    Request nextRequestInQueue = requests.stream().findFirst().orElse(null);
    if (nextRequestInQueue == null || nextRequestInQueue.getRequestType() != RequestType.RECALL) {
      return completedFuture(succeeded(records));
    }

    final Loan loanToRecall = records.getLoan();
    final LoanPolicy loanPolicy = loanToRecall.getLoanPolicy();

    // We don't need to apply the recall
    if (loanToRecall.isDueDateChangedByRecall()) {
      return completedFuture(succeeded(records));
    }

    return completedFuture(loanPolicy
      .recall(loanToRecall)
      .map(records::withLoan))
      .thenCompose(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement));
  }
}
