package org.folio.circulation.domain;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.ItemRepository;
import org.folio.circulation.support.Result;

public class MoveRequestHelper {
  private final ItemRepository itemRepository;
  private final LoanRepository loanRepository;
  private final RequestQueueRepository requestQueueRepository;

  public MoveRequestHelper(ItemRepository itemRepository, LoanRepository loanRepository,
      RequestQueueRepository requestQueueRepository) {
    this.itemRepository = itemRepository;
    this.loanRepository = loanRepository;
    this.requestQueueRepository = requestQueueRepository;
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> withDestinationItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(r -> r.map(requestAndRelatedRecords::withItem))
        .thenComposeAsync(r -> r.after(this::withDestinationItemLoan));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> withDestinationItemLoan(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return loanRepository.findOpenLoanForRequest(requestAndRelatedRecords.getRequest())
        .thenApply(r -> r.map(requestAndRelatedRecords::withLoan));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> withDestinationItemRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getDestinationItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> withOriginalItem(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return itemRepository.fetchById(requestAndRelatedRecords.getOriginalItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withItem));
  }

  CompletableFuture<Result<RequestAndRelatedRecords>> withOriginalItemRequestQueue(
      RequestAndRelatedRecords requestAndRelatedRecords) {
    return requestQueueRepository.get(requestAndRelatedRecords.getOriginalItemId())
        .thenApply(result -> result.map(requestAndRelatedRecords::withRequestQueue));
  }
}
