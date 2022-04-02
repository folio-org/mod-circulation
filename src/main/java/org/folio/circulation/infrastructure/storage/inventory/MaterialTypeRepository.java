package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.MaterialTypeMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class MaterialTypeRepository {
  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<Result<MaterialType>> getFor(Item item) {
    final String materialTypeId = item.getMaterialTypeId();

    if (isNull(materialTypeId)) {
      return Result.ofAsync(MaterialType::unknown);
    }

    final var mapper = new MaterialTypeMapper();

    return SingleRecordFetcher.json(materialTypesStorageClient, "material types",
      response -> succeeded(null))
      .fetch(materialTypeId)
      .thenApply(r -> r.map(mapper::toDomain));
  }

  public CompletableFuture<Result<MultipleRecords<MaterialType>>> getMaterialTypes(
    MultipleRecords<Item> inventoryRecords) {

    final var mapper = new MaterialTypeMapper();

    final var materialTypeIds = inventoryRecords.toKeys(Item::getMaterialTypeId);

    final var fetcher
      = findWithMultipleCqlIndexValues(materialTypesStorageClient, "mtypes", mapper::toDomain);

    return fetcher.findByIds(materialTypeIds);
  }
}
