package org.folio.circulation.infrastructure.storage.requests;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.RequestLevel.ITEM;
import static org.folio.circulation.domain.RequestLevel.TITLE;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatch;
import static org.folio.circulation.support.http.client.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.http.client.PageLimit.oneThousand;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestAndRelatedRecords;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.resources.context.RenewalContext;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.PageLimit;
import org.folio.circulation.support.results.Result;

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

  public CompletableFuture<Result<LoanAndRelatedRecords>> get(LoanAndRelatedRecords records) {
    return getQueue(records)
      .thenApply(mapResult(records::withRequestQueue));
  }

  private CompletableFuture<Result<RequestQueue>> getQueue(LoanAndRelatedRecords records) {
    TlrSettingsConfiguration tlrSettings = records.getTlrSettings();

    return tlrSettings != null && tlrSettings.isTitleLevelRequestsFeatureEnabled()
      ? getByInstanceId(records.getItem().getInstanceId())
      : getByItemId(records.getItem().getItemId());
  }

  public CompletableFuture<Result<RenewalContext>> get(RenewalContext renewalContext) {
    return getByItemId(renewalContext.getLoan().getItemId())
      .thenApply(result -> result.map(renewalContext::withRequestQueue));
  }

  public CompletableFuture<Result<RequestQueue>> get(RequestAndRelatedRecords requestAndRelatedRecords) {
    boolean tlrFeatureEnabled = requestAndRelatedRecords.getRequest().getTlrSettingsConfiguration()
      .isTitleLevelRequestsFeatureEnabled();
    Request request = requestAndRelatedRecords.getRequest();

    if (tlrFeatureEnabled) {
      return getByInstanceId(request.getInstanceId());
    }
    else {
      return getByItemId(request.getItemId());
    }
  }

  public CompletableFuture<Result<RequestQueue>> getByInstanceId(String instanceId) {
    return get("instanceId", instanceId, List.of(ITEM.getValue(), TITLE.getValue()));
  }

  public CompletableFuture<Result<RequestQueue>> getByItemId(String itemId) {
    return get("itemId", itemId, List.of(ITEM.getValue()));
  }

  private CompletableFuture<Result<RequestQueue>> get(String idFieldName, String id,
    List<String> requestLevels) {

    final Result<CqlQuery> itemIdQuery = exactMatch(idFieldName, id);
    final Result<CqlQuery> statusQuery = exactMatchAny("status", RequestStatus.openStates());
    final Result<CqlQuery> requestLevelQuery = exactMatchAny("requestLevel", requestLevels);

    return itemIdQuery.combine(statusQuery, CqlQuery::and)
      .combine(requestLevelQuery, CqlQuery::and)
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
