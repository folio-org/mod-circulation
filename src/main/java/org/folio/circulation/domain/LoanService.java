package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.domain.policy.library.ClosedLibraryStrategyService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.utils.ClockUtil;

public class LoanService {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ClosedLibraryStrategyService closedLibraryStrategyService;

  public LoanService(Clients clients) {
    closedLibraryStrategyService = ClosedLibraryStrategyService.using(clients,
      ClockUtil.getZonedDateTime(), false);
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> truncateLoanWhenItemRecalled(
    LoanAndRelatedRecords records) {

    log.debug("truncateLoanWhenItemRecalled:: parameters records: {}", () -> records);
    RequestQueue requestQueue = records.getRequestQueue();
    Collection<Request> requests = requestQueue.getRequests();

    if(requests.isEmpty()) {
      log.info("truncateLoanWhenItemRecalled:: requests is empty");
      return completedFuture(succeeded(records));
    }

    if (!requestQueue.containsRequestOfTypeForItem(RequestType.RECALL, records.getItem())) {
      log.info("truncateLoanWhenItemRecalled:: request queue does not contain recall type");
      return completedFuture(succeeded(records));
    }

    final Loan loanToRecall = records.getLoan();
    final LoanPolicy loanPolicy = loanToRecall.getLoanPolicy();

    if (loanToRecall.wasDueDateChangedByRecall()) {
      log.info("truncateLoanWhenItemRecalled:: due date was changed by recall");
      return completedFuture(succeeded(records));
    }

    return completedFuture(loanPolicy
      .recall(loanToRecall)
      .map(records::withLoan))
      .thenCompose(r -> r.after(closedLibraryStrategyService::applyClosedLibraryDueDateManagement));
  }
}
