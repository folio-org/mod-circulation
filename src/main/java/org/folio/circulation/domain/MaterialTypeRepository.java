package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class MaterialTypeRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getMaterialType(
    LoanAndRelatedRecords relatedRecords) {

    final InventoryRecords inventoryRecords = relatedRecords.getInventoryRecords();

    //Cannot find material type for unknown item
    if(inventoryRecords.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.success(relatedRecords));
    }

    String materialTypeId = inventoryRecords.getMaterialTypeId();

    return getMaterialType(materialTypeId,
      inventoryRecords.getItemId())
      .thenApply(result -> result.map(relatedRecords::withMaterialType));
  }

  private CompletableFuture<HttpResult<JsonObject>> getMaterialType(
    String materialTypeId,
    String itemId) {

    CompletableFuture<Response> getLocationCompleted = new CompletableFuture<>();

    materialTypesStorageClient.get(materialTypeId, getLocationCompleted::complete);

    //TODO: Add functions to explicitly distinguish between fatal not found
    // and allowable not found
    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      }
      else {
        log.warn("Could not get material type {} for item {}",
          materialTypeId, itemId);

        return HttpResult.success(null);
      }
    };

    return getLocationCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }
}
