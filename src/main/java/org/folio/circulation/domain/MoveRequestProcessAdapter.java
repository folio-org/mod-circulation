package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.infrastructure.storage.inventory.ItemRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.infrastructure.storage.requests.RequestRepository;
import org.folio.circulation.support.results.Result;

public class MoveRequestProcessAdapter {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final ItemRepository itemRepository;
  private final LoanRepository loanRepository;
  private final RequestRepository requestRepository;

  public MoveRequestProcessAdapter(ItemRepository itemRepository, LoanRepository loanRepository,
    RequestRepository requestRepository) {

    this.itemRepository = itemRepository;
    this.loanRepository = loanRepository;
    this.requestRepository = requestRepository;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findDestinationItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("findDestinationItem:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);

    return itemRepository.fetchById(requestAndRelatedRecords.getDestinationItemId())
      .thenApply(r -> r.map(requestAndRelatedRecords::withItem))
      .thenComposeAsync(r -> r.after(this::findLoanForItem));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> findLoanForItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    return loanRepository.findOpenLoanForRequest(requestAndRelatedRecords.getRequest())
      .thenApply(r -> r.map(requestAndRelatedRecords::withLoan));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findSourceItem(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("findSourceItem:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);

    return itemRepository.fetchById(requestAndRelatedRecords.getSourceItemId())
      .thenApply(result -> result.map(requestAndRelatedRecords::withItem))
      .thenComposeAsync(r -> r.after(this::findLoanForItem));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequest(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    log.debug("getRequest:: parameters requestAndRelatedRecords: {}",
      () -> requestAndRelatedRecords);

    return requestRepository.getById(requestAndRelatedRecords.getRequest().getId())
      .thenApply(r -> r.map(requestAndRelatedRecords::withRequest));
  }
}
