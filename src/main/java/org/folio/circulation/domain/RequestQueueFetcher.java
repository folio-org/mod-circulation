package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class RequestQueueFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final Clients clients;

  public RequestQueueFetcher(Clients clients) {
    this.clients = clients;
  }

  public CompletableFuture<HttpResult<RequestQueue>> get(String itemId) {
    final String cqlQuery;

    try {
      cqlQuery = URLEncoder.encode(
        String.format("itemId==%s and fulfilmentPreference==%s and status==(%s or %s) sortBy requestDate/sort.ascending",
        itemId, RequestFulfilmentPreference.HOLD_SHELF,
          RequestStatus.OPEN_AWAITING_PICKUP, RequestStatus.OPEN_NOT_YET_FILLED),
        String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      return completedFuture(HttpResult.failure(
        new ServerErrorFailure("Failed to encode CQL query for fetching request queue")));
    }

    CompletableFuture<HttpResult<RequestQueue>> requestQueueFetched = new CompletableFuture<>();

    this.clients.requestsStorage().getMany(cqlQuery, 1000, 0, fetchRequestsResponse -> {
      if (fetchRequestsResponse.getStatusCode() == 200) {
        final JsonArray foundRequests = fetchRequestsResponse.getJson().getJsonArray("requests");

        log.info("Found request queue: {}", foundRequests.encodePrettily());

        requestQueueFetched.complete(HttpResult.success(
          new RequestQueue(JsonArrayHelper.toList(foundRequests))));
      } else {
        requestQueueFetched.complete(HttpResult.failure(new ServerErrorFailure(
          String.format("Failed to fetch request queue: %s: %s",
            fetchRequestsResponse.getStatusCode(), fetchRequestsResponse.getBody()))));
      }
    });

    return requestQueueFetched;
  }
}
