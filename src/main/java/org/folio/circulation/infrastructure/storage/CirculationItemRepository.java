package org.folio.circulation.infrastructure.storage;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseInterpreter;
import org.folio.circulation.support.results.Result;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import static java.lang.String.format;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.http.ResponseMapping.mapUsingJson;

import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;

public class CirculationItemRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient circulationItemClient;

  public CirculationItemRepository(CollectionResourceClient circulationItemClient) {
    this.circulationItemClient = circulationItemClient;
  }
  public CompletableFuture<Result<JsonObject>> findCirculationItemByBarcode(String barcode) {

    log.debug("findCirculationItemByBarcode:: parameters barcode: {}", barcode);
    if(barcode == null) {
      log.info("findCirculationItemByBarcode:: barcode is null");
      return ofAsync(() -> null);
    }

    var interpreter = new ResponseInterpreter<JsonObject>()
      .flatMapOn(200, mapUsingJson(identity()))
      .otherwise(response -> succeeded(null));

    return circulationItemClient.getManyWithRawQueryStringParameters(format("barcode=%s", barcode))
      .thenApply(flatMapResult(interpreter::apply));
  }

  public CompletableFuture<Result<Response>> updateItem(String itemId, JsonObject representation) {
    return circulationItemClient.put(itemId, representation);
  }

}
