package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class RequestQueueFetcher {
  private final Clients clients;
  private final Consumer<String> failureConsumer;

  public RequestQueueFetcher(Clients clients, Consumer<String> failureConsumer) {
    this.clients = clients;
    this.failureConsumer = failureConsumer;
  }

  public CompletableFuture<JsonObject> updateOnCheckOut(JsonObject loan) {
    CompletableFuture<JsonObject> requestUpdated = new CompletableFuture<>();

    get(loan.getString("itemId"), requestQueue -> {
      if (requestQueue.hasOutstandingRequests()) {
        JsonObject firstRequest = requestQueue.getFirst();

        firstRequest.put("status", RequestStatus.CLOSED_FILLED);

        this.clients.requestsStorage().put(firstRequest.getString("id"), firstRequest,
          updateRequestResponse -> {

            if (updateRequestResponse.getStatusCode() == 204) {
              requestUpdated.complete(firstRequest);
            } else {
              failureConsumer.accept(
                String.format("Failed to update request: %s: %s",
                  updateRequestResponse.getStatusCode(), updateRequestResponse.getBody()));
            }
          });
      } else {
        requestUpdated.complete(null);
      }
    });


    return requestUpdated;
  }

  public void get(String itemId, Consumer<RequestQueue> onSuccess) {
    final String cqlQuery;

    try {
      cqlQuery = URLEncoder.encode(String.format("itemId==%s and fulfilmentPreference==%s",
        itemId, RequestFulfilmentPreference.HOLD_SHELF),
        String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      this.failureConsumer.accept("Failed to encode CQL query for fetching request queue");
      return;
    }

    this.clients.requestsStorage().getMany(cqlQuery, 1000, 0, fetchRequestsResponse -> {
      if(fetchRequestsResponse.getStatusCode() == 200) {
        onSuccess.accept(new RequestQueue(JsonArrayHelper.toList(
          fetchRequestsResponse.getJson().getJsonArray("requests"))));
      }
      else {
        this.failureConsumer.accept(
          String.format("Failed to fetch request queue: %s: %s",
            fetchRequestsResponse.getStatusCode(), fetchRequestsResponse.getBody()));
      }
    });
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
