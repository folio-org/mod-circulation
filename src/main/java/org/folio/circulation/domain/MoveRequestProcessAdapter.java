package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class MoveRequestProcessAdapter {
  private final ItemRepository itemRepository;
  private final LoanRepository loanRepository;
  private final RequestRepository requestRepository;
  private final RequestQueueRepository requestQueueRepository;

  public MoveRequestProcessAdapter(ItemRepository itemRepository, LoanRepository loanRepository,
      RequestRepository requestRepository, RequestQueueRepository requestQueueRepository) {
    this.itemRepository = itemRepository;
    this.loanRepository = loanRepository;
    this.requestRepository = requestRepository;
    this.requestQueueRepository = requestQueueRepository;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> findDestinationItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getDestinationItemId())
      .thenApply(r -> r.map(requestAndRelatedRecords::withItem))
      .thenComposeAsync(r -> r.after(this::findLoanForItem));
  }

  private CompletableFuture<Result<RequestAndRelatedRecords>> findLoanForItem(
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
      .thenApply(result -> result.map(requestAndRelatedRecords::withItem))
      .thenComposeAsync(r -> r.after(this::findLoanForItem));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getSourceRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getSourceItemId())
      .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> getRequest(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestRepository.getById(requestAndRelatedRecords.getRequest().getId())
      .thenApply(r -> r.map(requestAndRelatedRecords::withRequest));
  }
}
