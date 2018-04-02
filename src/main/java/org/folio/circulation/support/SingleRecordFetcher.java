package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

public class SingleRecordFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient client;
  private final String recordType;

  public SingleRecordFetcher(CollectionResourceClient client, String recordType) {
    this.client = client;
    this.recordType = recordType;
  }

  public CompletableFuture<HttpResult<JsonObject>> fetchSingleRecord(
    String id) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();

    log.info("Fetching {} with ID: {}", recordType, id);

    client.get(id, itemRequestCompleted::complete);

    return itemRequestCompleted
      .thenApply(this::mapToResult)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapToResult(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      } else {
        return HttpResult.success(null);
      }
    }
    else {
      //TODO: Replace with failure result
      log.warn("Did not receive response to request");
      return HttpResult.success(null);
    }
  }
}
