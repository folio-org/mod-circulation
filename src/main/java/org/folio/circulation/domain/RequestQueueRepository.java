package org.folio.circulation.domain;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.support.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CqlQuery;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestQueueRepository {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

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
      String unencodedQuery = String.format(
        "itemId==%s and status==(\"%s\" or \"%s\" or \"%s\") sortBy position/sort.ascending",
        itemId,
        RequestStatus.OPEN_AWAITING_PICKUP.getValue(),
        RequestStatus.OPEN_NOT_YET_FILLED.getValue(),
        RequestStatus.OPEN_IN_TRANSIT.getValue());

    final int maximumSupportedRequestQueueSize = 1000;

    log.info("Fetching request queue: '{}'", unencodedQuery);

    return requestRepository.findBy(new CqlQuery(unencodedQuery), maximumSupportedRequestQueueSize)
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
