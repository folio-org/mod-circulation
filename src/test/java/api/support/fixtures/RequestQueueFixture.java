package api.support.fixtures;

import static api.support.http.InterfaceUrls.reorderRequestQueueForInstanceUrl;
import static api.support.http.InterfaceUrls.reorderRequestQueueForItemUrl;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class RequestQueueFixture {
  private final RestAssuredClient restAssuredClient;

  public RequestQueueFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response attemptReorderQueueForItem(String itemId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderRequestQueueForItemUrl(itemId),
      "attempt-to-reorder-request-queue-for-item");
  }

  public Response attemptReorderQueueForInstance(String instanceId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderRequestQueueForInstanceUrl(instanceId),
      "attempt-to-reorder-request-queue-for-instance");
  }

  public JsonObject reorderQueueForItem(String itemId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderRequestQueueForItemUrl(itemId),
      200, "reorder-request-queue-for-item").getJson();
  }

  public JsonObject reorderQueueForInstance(String instanceId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderRequestQueueForInstanceUrl(instanceId),
      200, "reorder-request-queue-for-instance").getJson();
  }

  //TODO: Consolidate with similar method in requests fixture
  public JsonObject retrieveQueueForItem(String itemId) {
    return restAssuredClient.get(InterfaceUrls.requestQueueForItemUrl(itemId), 200,
      "get-request-queue-for-item").getJson();
  }

  public JsonObject retrieveQueueForInstance(String instanceId) {
    return restAssuredClient.get(InterfaceUrls.requestQueueForInstanceUrl(instanceId), 200,
      "get-request-queue-for-instance").getJson();
  }
}
