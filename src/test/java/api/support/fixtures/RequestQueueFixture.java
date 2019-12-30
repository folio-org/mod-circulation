package api.support.fixtures;

import static api.support.http.InterfaceUrls.reorderQueueUrl;
import static api.support.http.InterfaceUrls.requestQueueUrl;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.folio.circulation.support.http.client.Response;

import api.support.RestAssuredClient;
import io.vertx.core.json.JsonObject;

public class RequestQueueFixture {
  private final RestAssuredClient restAssuredClient;

  public RequestQueueFixture(RestAssuredClient restAssuredClient) {
    this.restAssuredClient = restAssuredClient;
  }

  public Response attemptReorderQueue(String itemId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderQueueUrl(itemId),
      "attempt-to-reorder-request-queue");
  }

  public JsonObject reorderQueue(String itemId, JsonObject reorderQueue) {
    return restAssuredClient.post(reorderQueue, reorderQueueUrl(itemId),
      200, "reorder-request-queue").getJson();
  }

  //TODO: Consolidate with similar method in requests fixture
  public JsonObject retrieveQueue(String itemId) {
    return restAssuredClient.get(requestQueueUrl(itemId), 200,
      "get-request-queue").getJson();
  }
}
