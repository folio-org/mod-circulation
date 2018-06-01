package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MaterialTypeRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<HttpResult<Item>> getMaterialType(
    Item item) {

    return getMaterialType(item.getMaterialTypeId(), item.getItemId())
      .thenApply(result -> result.map(item::withMaterialType));
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getMaterialType(
    LoanAndRelatedRecords relatedRecords) {

    final Item item = relatedRecords.getInventoryRecords();

    //Cannot find material type for unknown item
    if(item.isNotFound()) {
      return CompletableFuture.completedFuture(HttpResult.success(relatedRecords));
    }

    String materialTypeId = item.getMaterialTypeId();

    return getMaterialType(materialTypeId,
      item.getItemId())
      .thenApply(result -> result.map((JsonObject t) -> relatedRecords.withMaterialType()));
  }

  private CompletableFuture<HttpResult<JsonObject>> getMaterialType(
    String materialTypeId,
    String itemId) {

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

    return materialTypesStorageClient.get(materialTypeId)
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  public CompletableFuture<HttpResult<Map<String, JsonObject>>> getMaterialTypes(
    Collection<Item> inventoryRecords) {

    List<String> materialTypeIds = inventoryRecords.stream()
      .map(Item::getMaterialTypeId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    CompletableFuture<Response> materialTypesFetched = new CompletableFuture<>();

    String materialTypesQuery = CqlHelper.multipleRecordsCqlQuery(materialTypeIds);

    materialTypesStorageClient.getMany(materialTypesQuery, materialTypeIds.size(), 0,
      materialTypesFetched::complete);

    return materialTypesFetched
      .thenApply(materialTypesResponse -> {
        if(materialTypesResponse.getStatusCode() != 200) {
          return HttpResult.failure(new ServerErrorFailure(
            String.format("Material Types request (%s) failed %s: %s",
              materialTypesQuery, materialTypesResponse.getStatusCode(),
              materialTypesResponse.getBody())));
        }

        List<JsonObject> materialTypes = JsonArrayHelper.toList(
          materialTypesResponse.getJson().getJsonArray("mtypes"));

        return HttpResult.success(materialTypes.stream().collect(
          Collectors.toMap(m -> m.getString("id"),
            Function.identity())));
      });
  }
}
