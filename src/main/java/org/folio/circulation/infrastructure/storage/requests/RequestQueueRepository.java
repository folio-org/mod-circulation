package org.folio.circulation.infrastructure.storage.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.PageLimit.oneThousand;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.ItemRelatedRecord;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RequestQueueRepository {
  private static final Logger LOG = LogManager.getLogger(RequestQueueRepository.class);

  private static final PageLimit MAXIMUM_SUPPORTED_REQUEST_QUEUE_SIZE = oneThousand();
  private final RequestRepository requestRepository;

  private RequestQueueRepository(RequestRepository requestRepository) {
    this.requestRepository = requestRepository;
  }

  public static RequestQueueRepository using(Clients clients) {
    return new RequestQueueRepository(RequestRepository.using(clients));
  }

  public CompletableFuture<Result<LoanAndRelatedRecords>> get(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return getByItemId(loanAndRelatedRecords.getLoan().getItemId())
      .thenApply(result -> result.map(loanAndRelatedRecords::withRequestQueue));
  }

  public CompletableFuture<Result<RenewalContext>> get(RenewalContext renewalContext) {
    return getByItemId(renewalContext.getLoan().getItemId())
      .thenApply(result -> result.map(renewalContext::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> get(ItemRelatedRecord itemRelatedRecord) {
    return getByItemId(itemRelatedRecord.getItemId());
  }

  public CompletableFuture<Result<RequestQueue>> getByInstanceId(String instanceId) {
    return get("instanceId", instanceId);
  }

  public CompletableFuture<Result<RequestQueue>> getByItemId(String itemId) {
    return get("itemId", itemId);
  }

  private CompletableFuture<Result<RequestQueue>> get(String idFieldName, String id) {
    final Result<CqlQuery> itemIdQuery = exactMatch(idFieldName, id);
    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());

    return itemIdQuery.combine(statusQuery, CqlQuery::and)
      .map(q -> q.sortBy(ascending("position")))
      .after(query -> requestRepository.findBy(query,
        MAXIMUM_SUPPORTED_REQUEST_QUEUE_SIZE))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(RequestQueue::new));
  }

  public CompletableFuture<Result<RequestQueue>> getRequestQueueWithoutItemLookup(String itemId) {
    final Result<CqlQuery> itemIdQuery = exactMatch("itemId", itemId);
    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());

    return itemIdQuery.combine(statusQuery, CqlQuery::and)
      .map(q -> q.sortBy(ascending("position")))
      .after(query -> requestRepository.findByWithoutItems(query,
          MAXIMUM_SUPPORTED_REQUEST_QUEUE_SIZE))
      .thenApply(r -> r.map(MultipleRecords::getRecords))
      .thenApply(r -> r.map(RequestQueue::new));
  }

  public CompletableFuture<Result<RequestQueue>> updateRequestsWithChangedPositions(
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
