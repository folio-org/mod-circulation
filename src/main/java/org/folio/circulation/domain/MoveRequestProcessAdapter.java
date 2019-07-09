package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class MoveRequestProcessAdapter {
  private final ItemRepository itemRepository;
  private final LoanRepository loanRepository;
  private final RequestQueueRepository requestQueueRepository;

  public MoveRequestProcessAdapter(ItemRepository itemRepository, LoanRepository loanRepository,
      RequestQueueRepository requestQueueRepository) {
    this.itemRepository = itemRepository;
    this.loanRepository = loanRepository;
    this.requestQueueRepository = requestQueueRepository;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findDestinationItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(r -> r.map(requestAndRelatedRecords::withItem))
        .thenComposeAsync(r -> r.after(this::findLoanForDestinationItem));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findLoanForDestinationItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return loanRepository.findOpenLoanForRequest(requestAndRelatedRecords.getRequest())
        .thenApply(r -> r.map(requestAndRelatedRecords::withLoan));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getDestinationRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findSourceItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getSourceItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withItem));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getSourceRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getSourceItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }
}
