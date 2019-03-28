package org.folio.circulation.domain;

import static java.util.function.Function.identity;
import static org.folio.circulation.support.Result.succeeded;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.CqlHelper;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;

import io.vertx.core.json.JsonObject;

public class MaterialTypeRepository {
  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<Result<JsonObject>> getFor(Item item) {
    return SingleRecordFetcher.json(materialTypesStorageClient, "material types",
      response -> succeeded(null))
      .fetch(item.getMaterialTypeId());
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getMaterialTypes(
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
