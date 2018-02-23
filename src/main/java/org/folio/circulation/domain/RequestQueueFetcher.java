package org.folio.circulation.domain;

import org.folio.circulation.support.Clients;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.ServerErrorFailure;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class RequestQueueFetcher {
  private final Clients clients;

  public RequestQueueFetcher(Clients clients) {
    this.clients = clients;
  }

  public CompletableFuture<HttpResult<RequestQueue>> get(String itemId) {
    final String cqlQuery;

    try {
      cqlQuery = URLEncoder.encode(String.format("itemId==%s and fulfilmentPreference==%s",
        itemId, RequestFulfilmentPreference.HOLD_SHELF),
        String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      return completedFuture(HttpResult.failure(
        new ServerErrorFailure("Failed to encode CQL query for fetching request queue")));
    }

    CompletableFuture<HttpResult<RequestQueue>> requestQueueFetched = new CompletableFuture<>();

    this.clients.requestsStorage().getMany(cqlQuery, 1000, 0, fetchRequestsResponse -> {
      if (fetchRequestsResponse.getStatusCode() == 200) {
        requestQueueFetched.complete(HttpResult.success(new RequestQueue(JsonArrayHelper.toList(
          fetchRequestsResponse.getJson().getJsonArray("requests")))));
      } else {
        requestQueueFetched.complete(HttpResult.failure(new ServerErrorFailure(
          String.format("Failed to fetch request queue: %s: %s",
            fetchRequestsResponse.getStatusCode(), fetchRequestsResponse.getBody()))));
      }
    });

    return requestQueueFetched;
  }
}
