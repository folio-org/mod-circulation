package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.Result;

public class RequestQueueRepository {
  private final RequestRepository requestRepository;

  private RequestQueueRepository(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public static RequestQueueRepository using(Clients clients) {
    return new RequestQueueRepository(RequestRepository.using(clients));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> get(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return get(loanAndRelatedRecords.getLoan().getItemId())
      .thenApply(result -> result.map(loanAndRelatedRecords::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> get(ItemRelatedRecord itemRelatedRecord) {
    return get(itemRelatedRecord.getItemId());
  }

  public CompletableFuture<Result<RequestQueue>> get(String itemId) {
    final Result<CqlQuery> itemIdQuery = exactMatch("itemId", itemId);
    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());

    final int maximumSupportedRequestQueueSize = 1000;

    return itemIdQuery.combine(statusQuery, CqlQuery::and)
      .map(q -> q.sortBy(ascending("position")))
      .after(query -> requestRepository.findBy(query, maximumSupportedRequestQueueSize))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(RequestQueue::new));
  }

  public CompletableFuture<Result<RequestQueue>> getRequestQueueWithoutItemLookup(String itemId) {
    final Result<CqlQuery> itemIdQuery = exactMatch("itemId", itemId);
    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());

    final int maximumSupportedRequestQueueSize = 1000;

    return itemIdQuery.combine(statusQuery, CqlQuery::and)
      .map(q -> q.sortBy(ascending("position")))
      .after(query -> requestRepository.findByWithoutItems(query, maximumSupportedRequestQueueSize))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(RequestQueue::new));
  }

  CompletableFuture<Result<RequestQueue>> updateRequestsWithChangedPositions(
    RequestQueue requestQueue) {

    final Collection<Request> changedRequests = requestQueue.getRequestsWithChangedPosition();

    if(changedRequests.isEmpty()) {
      return completedFuture(succeeded(requestQueue));
    }

    //Chain updating the requests together in sequence
    //Have to be updated one at a time so as to avoid position clashes
    //(Which might be a constraint in storage)

    //Need an initial future to hang off
    CompletableFuture<Result<Request>> requestUpdated = completedFuture(succeeded(null));

    for (Request request : changedRequests) {
      requestUpdated = requestUpdated.thenComposeAsync(
        r -> r.after(notUsed -> requestRepository.update(request)));
    }

    return requestUpdated.thenApply(r -> r.map(notUsed -> requestQueue));
  }
}
