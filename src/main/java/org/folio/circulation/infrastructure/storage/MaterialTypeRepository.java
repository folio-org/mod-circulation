package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static org.folio.circulation.support.JsonKeys.byId;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;

import io.vertx.core.json.JsonObject;

public class MaterialTypeRepository {
  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<Result<JsonObject>> getFor(Item item) {
    final String materialTypeId = item.getMaterialTypeId();

    if(isNull(materialTypeId)) {
      return Result.ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(materialTypesStorageClient, "material types",
      response -> succeeded(null))
      .fetch(materialTypeId);
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getMaterialTypes(
    Collection<Item> inventoryRecords) {

    List<String> materialTypeIds = inventoryRecords.stream()
      .map(Item::getMaterialTypeId)
      .filter(StringUtils::isNotBlank)
      .distinct()
      .collect(Collectors.toList());

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = findWithMultipleCqlIndexValues(materialTypesStorageClient, "mtypes", identity());

    return fetcher.findByIds(materialTypeIds)
      .thenApply(mapResult(materialTypes -> materialTypes.toMap(byId())));
  }
}
