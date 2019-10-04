package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlQuery.exactMatch;
import static org.folio.circulation.support.CqlQuery.exactMatchAny;
import static org.folio.circulation.support.CqlSortBy.ascending;
import static org.folio.circulation.support.Result.succeeded;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;
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

  CompletableFuture<Result<RequestQueue>> reorderRequests(RequestQueue requestQueue) {
    Collection<Request> requestsWithChangedPosition = requestQueue
      .getRequestsWithChangedPosition();

    if (requestsWithChangedPosition.isEmpty()) {
      LOG.info("No requests with changed positions found");
      return completedFuture(succeeded(requestQueue));
    }

    // Release all positions by removing it from requests which previously held them
    // Otherwise we get constraint violation
    return releaseAllRequestPositions(requestQueue)
      // Now we are safe to set new positions.
      .thenCompose(r -> r.after(this::updateRequestsWithNewPositions))
      // Order the queue by position
      .thenApply(r -> r.map(this::orderQueueByRequestPosition));
  }

  private CompletableFuture<Result<RequestQueue>> releaseAllRequestPositions(
    RequestQueue requestQueue) {

    CompletableFuture<Result<Request>> overallRequestUpdateFuture = completedFuture(succeeded(null));
    List<CompletableFuture<Result<Request>>> allRemoveRequestFutures = new ArrayList<>();

    for (Request changedRequest : requestQueue.getRequestsWithChangedPosition()) {
      LOG.debug("Releasing position [{}] occupied by [{}]",
        changedRequest.getPreviousPosition(), changedRequest.getId());

      allRemoveRequestFutures.add(
        overallRequestUpdateFuture.thenComposeAsync(
          r -> r.after(notUsed -> {
            // Remove position attribute
            Request oldRequestWithNoPosition = changedRequest.withNoPosition();
            return requestRepository.update(oldRequestWithNoPosition);
          })
        ));
    }

    return CompletableFuture.allOf(allRemoveRequestFutures.toArray(new CompletableFuture[0]))
      .thenApply(notUsed -> succeeded(requestQueue));
  }

  private CompletableFuture<Result<RequestQueue>> updateRequestsWithNewPositions(RequestQueue requestQueue) {
    CompletableFuture<Result<Request>> overallRequestUpdateFuture = completedFuture(succeeded(null));
    List<CompletableFuture<Result<Request>>> allUpdateRequestFutures = new ArrayList<>();

    for (Request changedRequests : requestQueue.getRequestsWithChangedPosition()) {
      LOG.debug("Processing request [{}] at position [{}]",
        changedRequests.getId(), changedRequests.getPosition());

      allUpdateRequestFutures.add(
        overallRequestUpdateFuture.thenComposeAsync(
          r -> r.after(notUsed -> requestRepository.update(changedRequests))
        )
      );
    }

    return CompletableFuture.allOf(allUpdateRequestFutures.toArray(new CompletableFuture[0]))
      // Error handling block
      .thenApply(notUsed -> {
        boolean failedRequestsFound = allUpdateRequestFutures.stream()
          .map(CompletableFuture::join)
          .anyMatch(result -> result == null || result.failed());

        return succeeded(requestQueue)
          .failWhen(
            r -> succeeded(failedRequestsFound),
            r -> new ServerErrorFailure("Some requests have not been updated. Fetch the queue and try again.")
          );
      });
  }

  private RequestQueue orderQueueByRequestPosition(RequestQueue queue) {
    List<Request> requests = queue.getRequests()
      .stream()
      .sorted(Comparator.comparingInt(Request::getPosition))
      .collect(Collectors.toList());

    return new RequestQueue(requests);
  }
}
