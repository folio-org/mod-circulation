package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.CqlHelper.encodeQuery;
import static org.folio.circulation.support.HttpResult.succeeded;

public class RequestQueueRepository {
  private final CollectionResourceClient requestsStorageClient;
  private final RequestRepository requestRepository;

  private RequestQueueRepository(
    CollectionResourceClient requestsStorageClient,
    RequestRepository requestRepository) {

    this.requestsStorageClient = requestsStorageClient;
    this.requestRepository = requestRepository;
  }

  public static RequestQueueRepository using(Clients clients) {
    return new RequestQueueRepository(clients.requestsStorage(),
      RequestRepository.using(clients));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> get(
    LoanAndRelatedRecords loanAndRelatedRecords) {

    return get(loanAndRelatedRecords.getLoan().getItemId())
      .thenApply(result -> result.map(loanAndRelatedRecords::withRequestQueue));
  }

  public CompletableFuture<HttpResult<RequestQueue>> get(String itemId) {
      String unencodedQuery = String.format(
        "itemId==%s and status==(\"%s\" or \"%s\") sortBy requestDate/sort.ascending",
        itemId,
        RequestStatus.OPEN_AWAITING_PICKUP,
        RequestStatus.OPEN_NOT_YET_FILLED);

    return encodeQuery(unencodedQuery).after(
      query -> {
        final int maximumSupportedRequestQueueSize = 1000;

        return requestsStorageClient.getMany(query, maximumSupportedRequestQueueSize, 0)
          .thenApply(response ->
            MultipleRecords.from(response, Request::from, "requests")
              .map(MultipleRecords::getRecords)
              .map(RequestQueue::new));
      });
  }

  CompletableFuture<HttpResult<RequestQueue>> updateRequestsWithChangedPositions(
    RequestQueue requestQueue) {

    final Collection<Request> changedRequests = requestQueue.getRequestsWithChangedPosition();

    if(changedRequests.isEmpty()) {
      return completedFuture(succeeded(requestQueue));
    }

    //Chain updating the requests together in sequence
    //Have to be updated one at a time so as to avoid position clashes
    //(Which might be a constraint in storage)

    //Need an initial future to hang off
    CompletableFuture<HttpResult<Request>> requestUpdated = completedFuture(succeeded(null));

    for (Request request : changedRequests) {
      requestUpdated = requestUpdated.thenComposeAsync(
        r -> r.after(notUsed -> requestRepository.update(request)));
    }

    return requestUpdated.thenApply(r -> r.map(notUsed -> requestQueue));
  }
}
