package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class SingleRecordFetcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient client;
  private final String recordType;
  private final Function<Response, HttpResult<JsonObject>> resultOnFailure;

  SingleRecordFetcher(CollectionResourceClient client, String recordType) {
    this(client, recordType, response -> HttpResult.succeeded(null));
  }

  public SingleRecordFetcher(
    CollectionResourceClient client,
    String recordType,
    Function<Response, HttpResult<JsonObject>> resultOnFailure) {
    this.client = client;
    this.recordType = recordType;
    this.resultOnFailure = resultOnFailure;
  }

  public CompletableFuture<HttpResult<JsonObject>> fetchSingleRecord(String id) {
    log.info("Fetching {} with ID: {}", recordType, id);

    return client.get(id)
      .thenApply(this::mapToResult)
      .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
  }

  private HttpResult<JsonObject> mapToResult(Response response) {
    if(response != null) {
      log.info("Response received, status code: {} body: {}",
        response.getStatusCode(), response.getBody());

      if (response.getStatusCode() == 200) {
        return HttpResult.succeeded(response.getJson());
      } else {
        return this.resultOnFailure.apply(response);
      }
    }
    else {
      log.warn("Did not receive response to request");
      return HttpResult.failed(new ServerErrorFailure("Did not receive response to request"));
    }
  }
}
