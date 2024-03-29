package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.utils.LogUtil.multipleRecordsAsString;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.storage.mappers.MaterialTypeMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

public class MaterialTypeRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final String MATERIAL_TYPES = "mtypes";
  private final CollectionResourceClient materialTypesStorageClient;

  public MaterialTypeRepository(Clients clients) {
    materialTypesStorageClient = clients.materialTypesStorage();
  }

  public CompletableFuture<Result<MaterialType>> getFor(Item item) {
    log.debug("getFor:: parameters item: {}", item);
    final String materialTypeId = item.getMaterialTypeId();

    if (isNull(materialTypeId)) {
      log.info("getFor:: materialTypeId is null");
      return Result.ofAsync(() -> MaterialType.unknown(null));
    }

    final var mapper = new MaterialTypeMapper();

    return SingleRecordFetcher.json(materialTypesStorageClient, "material types",
      response -> succeeded(null))
      .fetch(materialTypeId)
      .thenApply(r -> r.map(mapper::toDomain));
  }

  public CompletableFuture<Result<MultipleRecords<MaterialType>>> getMaterialTypes(
    MultipleRecords<Item> inventoryRecords) {

    log.debug("getMaterialTypes:: parameters inventoryRecords: {}",
      () -> multipleRecordsAsString(inventoryRecords));

    final var mapper = new MaterialTypeMapper();

    final var materialTypeIds = inventoryRecords.toKeys(Item::getMaterialTypeId);

    final var fetcher
      = findWithMultipleCqlIndexValues(materialTypesStorageClient, MATERIAL_TYPES, mapper::toDomain);

    return fetcher.findByIds(materialTypeIds);
  }
}
