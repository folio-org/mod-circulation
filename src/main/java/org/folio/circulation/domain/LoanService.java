package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

public class LoanService {

  private final ClosedLibraryStrategyService closedLibraryStrategyService;

  public LoanService(Clients clients) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
      ClockUtil.getZonedDateTime(), false);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> truncateLoanWhenItemRecalled(
    LoanAndRelatedRecords records) {
    System.out.println("Is it coming here ?????");
    RequestQueue requestQueue = records.getRequestQueue();
    Collection<Request> requests = requestQueue.getRequests();

    if(requests.isEmpty()) {
      return completedFuture(succeeded(records));
    }

    if (!requestQueue.containsRequestOfTypeForItem(RequestType.RECALL, records.getItem())) {
      return completedFuture(succeeded(records));
    }

    final Loan loanToRecall = records.getLoan();
    final LoanPolicy loanPolicy = loanToRecall.getLoanPolicy();

    if (loanToRecall.wasDueDateChangedByRecall()) {
      System.out.println("At truncate loan and due date changed by recall");
      return completedFuture(succeeded(records));
    }

    return completedFuture(loanPolicy
      .recall(loanToRecall)
      .map(records::withLoan))
      .thenCompose(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement));
  }
}
