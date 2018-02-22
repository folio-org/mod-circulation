package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.JsonArrayHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RequestQueue {
  private final Clients clients;
  private final Consumer<String> failureConsumer;

  public RequestQueue(Clients clients, Consumer<String> failureConsumer) {
    this.clients = clients;
    this.failureConsumer = failureConsumer;
  }

  public CompletableFuture<JsonObject> updateOnCheckIn(JsonObject loan) {
    CompletableFuture<JsonObject> requestUpdated = new CompletableFuture<>();

    get(loan.getString("itemId"), requestQueue -> {
      if (hasOutstandingRequests(requestQueue)) {
        JsonObject firstRequest = requestQueue.get(0);

        firstRequest.put("status", RequestStatus.OPEN_AWAITING_PICKUP);

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

  public CompletableFuture<JsonObject> updateOnCheckOut(JsonObject loan) {
    CompletableFuture<JsonObject> requestUpdated = new CompletableFuture<>();

    get(loan.getString("itemId"), requestQueue -> {
      if (hasOutstandingRequests(requestQueue)) {
        JsonObject firstRequest = requestQueue.get(0);

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

  public void get(String itemId, Consumer<List<JsonObject>> onSuccess) {
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
        final List<JsonObject> requestQueue = JsonArrayHelper.toList(
          fetchRequestsResponse.getJson().getJsonArray("requests"));

        onSuccess.accept(requestQueue);
      }
      else {
        this.failureConsumer.accept(
          String.format("Failed to fetch request queue: %s: %s",
            fetchRequestsResponse.getStatusCode(), fetchRequestsResponse.getBody()));
      }
    });
  }

  private static boolean hasOutstandingRequests(Collection<JsonObject> requestQueue) {
    return !requestQueue.isEmpty();
  }
}
