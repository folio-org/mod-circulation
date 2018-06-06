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
  private final Supplier<HttpResult<JsonObject>> resultOnFailure;

  public SingleRecordFetcher(CollectionResourceClient client, String recordType) {
    this(client, recordType, () -> HttpResult.success(null));
  }

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    Supplier<HttpResult<JsonObject>> resultOnFailure) {
    this.client = client;
    this.recordType = recordType;
    this.resultOnFailure = resultOnFailure;
  }

  public CompletableFuture<HttpResult<JsonObject>> fetchSingleRecord(String id) {

    log.info("Fetching {} with ID: {}", recordType, id);

    return client.get(id)
      .thenApply(r -> mapToResult(r, this.resultOnFailure))
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapToResult(
    Response response,
    Supplier<HttpResult<JsonObject>> resultOnFailure) {

    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      } else {
        return resultOnFailure.get();
      }
    }
    else {
      log.warn("Did not receive response to request");
      return HttpResult.failure(new ServerErrorFailure("Did not receive response to request"));
    }
  }
}
