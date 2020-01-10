package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestQueueRepository {
  private static final Logger LOG = LoggerFactory.getLogger(RequestQueueRepository.class);
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

    Collection<Request> requestsWithChangedPosition = requestQueue
      .getRequestsWithChangedPosition();

    if (requestsWithChangedPosition.isEmpty()) {
      LOG.info("No requests with changed positions found");
      return completedFuture(succeeded(requestQueue));
    }

    return requestRepository.batchUpdate(requestsWithChangedPosition)
      .thenApply(r -> r.map(result -> requestQueue));
  }
}
