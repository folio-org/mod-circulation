package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.HttpResult.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UpdateRequestQueue {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final RequestQueueRepository requestQueueRepository;
  private final RequestRepository requestRepository;

  public UpdateRequestQueue(
    RequestQueueRepository requestQueueRepository,
    RequestRepository requestRepository) {

    this.requestQueueRepository = requestQueueRepository;
    this.requestRepository = requestRepository;
  }

  public static UpdateRequestQueue using(Clients clients) {
    return new UpdateRequestQueue(
      RequestQueueRepository.using(clients),
      RequestRepository.using(clients));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckIn(
    LoanAndRelatedRecords relatedRecords) {

    final RequestQueue requestQueue = relatedRecords.getRequestQueue();

    return onCheckIn(requestQueue)
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  public CompletableFuture<HttpResult<RequestQueue>> onCheckIn(
    RequestQueue requestQueue) {

    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request firstRequest = requestQueue.getHighestPriorityFulfillableRequest();

      firstRequest.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

      return requestRepository.update(firstRequest)
        .thenApply(result -> result.map(v -> requestQueue));

    } else {
      return completedFuture(succeeded(requestQueue));
    }
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    return onCheckOut(relatedRecords.getRequestQueue())
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  private CompletableFuture<HttpResult<RequestQueue>> onCheckOut(RequestQueue requestQueue) {
    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request firstRequest = requestQueue.getHighestPriorityFulfillableRequest();

      log.info("Closing request '{}'", firstRequest.getId());
      firstRequest.changeStatus(RequestStatus.CLOSED_FILLED);

      log.info("Removing request '{}' from queue", firstRequest.getId());
      requestQueue.remove(firstRequest);

      return requestRepository.update(firstRequest)
        .thenComposeAsync(r -> r.after(v ->
          requestQueueRepository.updateRequestsWithChangedPositions(requestQueue)));

    } else {
      return completedFuture(succeeded(requestQueue));
    }
  }

  CompletableFuture<HttpResult<RequestAndRelatedRecords>> onCancellation(
    RequestAndRelatedRecords requestAndRelatedRecords) {

    if(requestAndRelatedRecords.getRequest().isCancelled()) {
      return requestQueueRepository.updateRequestsWithChangedPositions(
        requestAndRelatedRecords.getRequestQueue())
        .thenApply(r -> r.map(requestAndRelatedRecords::withRequestQueue));
    }
    else {
      return completedFuture(succeeded(requestAndRelatedRecords));
    }
  }

  public CompletableFuture<HttpResult<Request>> onDeletion(Request request) {
    return requestQueueRepository.get(request.getItemId())
      .thenApply(r -> r.map(requestQueue -> {
        requestQueue.remove(request);
        return requestQueue;
      }))
      .thenComposeAsync(r -> r.after(
        requestQueueRepository::updateRequestsWithChangedPositions))
      .thenApply(r -> r.map(requestQueue -> request));
  }
}
