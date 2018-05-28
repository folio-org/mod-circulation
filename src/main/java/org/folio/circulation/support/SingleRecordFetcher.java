package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
    //TODO: Should be more discriminating about failure
    return fetchSingleRecord(id, () -> HttpResult.success(null));
  }

  public CompletableFuture<HttpResult<JsonObject>> fetchSingleRecord(
    String id,
    Supplier<HttpResult<JsonObject>> resultOnFailure) {
    CompletableFuture<Response> itemRequestCompleted = new CompletableFuture<>();

    log.info("Fetching {} with ID: {}", recordType, id);

    client.get(id, itemRequestCompleted::complete);

    return itemRequestCompleted
      .thenApply(r -> mapToResult(r, resultOnFailure))
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  public HttpResult<JsonObject> mapToResult(
    Response response,
    Supplier<HttpResult<JsonObject>> resultOnFailure) {

    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      } else {
        //TODO: Possibly should be constructor argument, rather than parameter?
        return resultOnFailure.get();
      }
    }
    else {
      log.warn("Did not receive response to request");
      return HttpResult.failure(new ServerErrorFailure("Did not receive response to request"));
    }
  }
}
