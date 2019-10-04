package api.support.fixtures;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.folio.circulation.support.http.client.OkapiHttpClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;

import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class RequestQueueFixture {
  private final OkapiHttpClient okapiHttpClient;

  public RequestQueueFixture(OkapiHttpClient client) {
    this.okapiHttpClient = client;
  }

  public Response attemptReorderQueue(String itemId, JsonObject reorderQueue) throws Exception {
    CompletableFuture<Response> reorderCompleted = new CompletableFuture<>();

    okapiHttpClient.post(InterfaceUrls.reorderQueueUrl(itemId), reorderQueue,
      ResponseHandler.any(reorderCompleted));

    return reorderCompleted.get(5, TimeUnit.SECONDS);
  }

  public JsonObject reorderQueue(String itemId, JsonObject reorderQueue) throws Exception {
    Response response = attemptReorderQueue(itemId, reorderQueue);

    assertThat(response.getStatusCode(), is(200));
    return response.getJson();
  }

  public JsonObject retrieveQueue(String itemId) throws Exception {
    CompletableFuture<Response> readCompleted = new CompletableFuture<>();

    okapiHttpClient.get(InterfaceUrls.getQueueUrl(itemId),
      ResponseHandler.json(readCompleted));

    Response response = readCompleted.get(5, TimeUnit.SECONDS);

    assertThat(response.getStatusCode(), is(200));
    return response.getJson();
  }
}
