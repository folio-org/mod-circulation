package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;

import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.CqlHelper.encodeQuery;

public class RequestQueueRepository {
  private final Clients clients;

  public RequestQueueRepository(Clients clients) {
    this.clients = clients;
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

        return clients.requestsStorage().getMany(query, maximumSupportedRequestQueueSize, 0)
          .thenApply(response ->
            MultipleRecords.from(response, Request::from, "requests")
              .map(MultipleRecords::getRecords)
              .map(RequestQueue::new));
      });
  }
}
