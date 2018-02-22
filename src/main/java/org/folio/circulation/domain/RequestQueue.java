package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.JsonArrayHelper;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RequestQueue {
  public CompletableFuture<JsonObject> updateRequestQueue(
    Clients clients,
    JsonObject loan,
    Consumer<String> failureConsumer) {

    CompletableFuture<JsonObject> requestUpdated = new CompletableFuture<>();

    final String cqlQuery;

    try {
      cqlQuery = URLEncoder.encode(String.format("itemId==%s and fulfilmentPreference==%s",
        loan.getString("itemId"), RequestFulfilmentPreference.HOLD_SHELF),
        String.valueOf(StandardCharsets.UTF_8));
    } catch (UnsupportedEncodingException e) {
      failureConsumer.accept("Failed to encode CQL query for fetching request queue");
      requestUpdated.completeExceptionally(e);
      return requestUpdated;
    }

    clients.requestsStorage().getMany(cqlQuery, 1000, 0, fetchRequestsResponse -> {
      if(fetchRequestsResponse.getStatusCode() == 200) {
        final List<JsonObject> requestQueue = JsonArrayHelper.toList(
          fetchRequestsResponse.getJson().getJsonArray("requests"));

        if(hasOutstandingRequests(requestQueue)) {
          JsonObject firstRequest = requestQueue.get(0);

          firstRequest.put("status", RequestStatus.OPEN_AWAITING_PICKUP);

          clients.requestsStorage().put(firstRequest.getString("id"), firstRequest,
            updateRequestResponse -> {

            if(updateRequestResponse.getStatusCode() == 204) {
              requestUpdated.complete(firstRequest);
            }
            else {
              failureConsumer.accept(
                String.format("Failed to update request: %s: %s",
                  updateRequestResponse.getStatusCode(), updateRequestResponse.getBody()));
            }
          });
        }
        else {
          requestUpdated.complete(null);
        }
      }
      else {
        failureConsumer.accept(
          String.format("Failed to fetch request queue: %s: %s",
            fetchRequestsResponse.getStatusCode(), fetchRequestsResponse.getBody()));
      }
    });

    return requestUpdated;
  }

  private static boolean hasOutstandingRequests(List<JsonObject> requestQueue) {
    return !requestQueue.isEmpty();
  }
}
