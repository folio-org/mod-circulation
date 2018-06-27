package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;

import java.util.concurrent.CompletableFuture;

public class UpdateRequestQueue {
  private final Clients clients;

  public UpdateRequestQueue(Clients clients) {
    this.clients = clients;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckIn(
    LoanAndRelatedRecords relatedRecords) {

    CompletableFuture<HttpResult<LoanAndRelatedRecords>> requestUpdated = new CompletableFuture<>();

    if (relatedRecords.getRequestQueue().hasOutstandingFulfillableRequests()) {
      Request firstRequest = relatedRecords.getRequestQueue().getHighestPriorityFulfillableRequest();

      firstRequest.changeStatus(RequestStatus.OPEN_AWAITING_PICKUP);

      clients.requestsStorage().put(firstRequest.getId(), firstRequest.asJson(),
        updateRequestResponse -> {
          if (updateRequestResponse.getStatusCode() == 204) {
            requestUpdated.complete(HttpResult.succeeded(relatedRecords));
          } else {
            requestUpdated.complete(HttpResult.failed(new ServerErrorFailure(
              String.format("Failed to update request: %s: %s",
                updateRequestResponse.getStatusCode(), updateRequestResponse.getBody()))));
          }
        });
    } else {
      requestUpdated.complete(HttpResult.succeeded(relatedRecords));
    }

    return requestUpdated;
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> onCheckOut(
    LoanAndRelatedRecords relatedRecords) {

    return onCheckOut(relatedRecords.getRequestQueue())
      .thenApply(result -> result.map(relatedRecords::withRequestQueue));
  }

  private CompletableFuture<HttpResult<RequestQueue>> onCheckOut(RequestQueue requestQueue) {
    CompletableFuture<HttpResult<RequestQueue>> requestUpdated = new CompletableFuture<>();

    if (requestQueue.hasOutstandingFulfillableRequests()) {
      Request firstRequest = requestQueue.getHighestPriorityFulfillableRequest();

      firstRequest.changeStatus(RequestStatus.CLOSED_FILLED);

      clients.requestsStorage().put(firstRequest.getId(), firstRequest.asJson(),
        updateRequestResponse -> {
          if (updateRequestResponse.getStatusCode() == 204) {
            requestUpdated.complete(HttpResult.succeeded(requestQueue));
          } else {
            requestUpdated.complete(HttpResult.failed(new ServerErrorFailure(
              String.format("Failed to update request: %s: %s",
                updateRequestResponse.getStatusCode(), updateRequestResponse.getBody()))));
          }
      });
    } else {
      requestUpdated.complete(HttpResult.succeeded(requestQueue));
    }

    return requestUpdated;
  }
}
