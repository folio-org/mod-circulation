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

public class LocationRepository {
  private CollectionResourceClient locationsStorageClient;

  public LocationRepository(Clients clients) {
    locationsStorageClient = clients.locationsStorage();
  }

  public CompletableFuture<Result<JsonObject>> getLocation(Item item) {
    return SingleRecordFetcher.json(locationsStorageClient, "locations",
      response -> succeeded(null))
      .fetch(item.getLocationId());
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getLocations(
    Collection<Item> inventoryRecords) {

    List<String> locationIds = inventoryRecords.stream()
      .map(Item::getLocationId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    return findByIds(locationIds)
      .thenApply(r -> r.map(locations ->
        locations.toMap(record -> record.getString("id"))));
  }

  private CompletableFuture<Result<MultipleRecords<JsonObject>>> findByIds(
    List<String> idList) {

    String locationsQuery = CqlHelper.multipleRecordsCqlQuery(idList);

    return locationsStorageClient.getMany(locationsQuery, idList.size(), 0)
      .thenApply(response ->
        MultipleRecords.from(response, identity(), "locations"));
  }
}
