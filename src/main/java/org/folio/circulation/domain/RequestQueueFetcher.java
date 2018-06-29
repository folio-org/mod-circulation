package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static org.folio.circulation.support.CqlHelper.encodeQuery;
import static org.folio.circulation.support.JsonArrayHelper.mapToList;

public class RequestQueueFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Clients clients;

  public RequestQueueFetcher(Clients clients) {
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
          .thenApply(
            response -> {
              if (response.getStatusCode() == 200) {
                final JsonArray foundRequests = response.getJson().getJsonArray("requests");

                log.info("Found request queue: {}", foundRequests.encodePrettily());

                return HttpResult.succeeded(
                  new RequestQueue(mapToList(foundRequests, Request::new)));
              } else {
                return HttpResult.failed(new ServerErrorFailure(
                  String.format("Failed to fetch request queue: %s: %s",
                    response.getStatusCode(), response.getBody())));
              }
        });
      });
  }
}
