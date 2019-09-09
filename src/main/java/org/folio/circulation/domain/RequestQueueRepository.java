package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.succeeded;

import java.util.ArrayList;
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

    final ArrayList<Request> changedRequests = new ArrayList<>(requestQueue.getRequestsWithChangedPosition());

    if(changedRequests.isEmpty()) {
      return completedFuture(succeeded(requestQueue));
    }

    CompletableFuture<Result<Request>> requestUpdated = completedFuture(succeeded(null));

    int index;
    Request request;
    boolean positionTaken = false;

    while (!changedRequests.isEmpty()) {
      index = 0;
      request = changedRequests.get(index);
      while (changedRequests.size() > 1
          && (positionTaken = requestQueue.positionPreviouslyTaken(request))
          && index + 1 < changedRequests.size()) {
        request = changedRequests.get(++index);
      }
      if (!positionTaken) {
        CompletableFuture<Result<Request>> updateFuture =
          requestRepository.update(request);
        requestUpdated = requestUpdated.thenComposeAsync(r ->
          r.after(notUsed -> updateFuture));
        request.freePreviousPosition();
        changedRequests.remove(index);
      }
    }
    return requestUpdated.thenApply(r -> r.map(notUsed -> requestQueue));
  }
}
