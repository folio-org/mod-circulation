package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.function.Function.identity;

public class MaterialTypeRepository {
  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<HttpResult<JsonObject>> getFor(Item item) {
    return SingleRecordFetcher.json(materialTypesStorageClient, "material types",
      response -> HttpResult.succeeded(null))
      .fetch(item.getMaterialTypeId());
  }

  public CompletableFuture<HttpResult<Map<String, JsonObject>>> getMaterialTypes(
    Collection<Item> inventoryRecords) {

    List<String> materialTypeIds = inventoryRecords.stream()
      .map(Item::getMaterialTypeId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    String materialTypesQuery = CqlHelper.multipleRecordsCqlQuery(materialTypeIds);

    return materialTypesStorageClient.getMany(materialTypesQuery, materialTypeIds.size(), 0)
      .thenApply(response ->
        MultipleRecords.from(response, identity(), "mtypes"))
      .thenApply(r -> r.map(materialTypes ->
        materialTypes.toMap(record -> record.getString("id"))));
  }
}
