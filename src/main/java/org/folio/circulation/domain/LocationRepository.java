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

public class LocationRepository {
  private CollectionResourceClient locationsStorageClient;

  public LocationRepository(Clients clients) {
    locationsStorageClient = clients.locationsStorage();
  }

  public CompletableFuture<HttpResult<JsonObject>> getLocation(Item item) {
    return SingleRecordFetcher.json(locationsStorageClient, "locations",
      response -> HttpResult.succeeded(null))
      .fetch(item.getLocationId());
  }

  public CompletableFuture<HttpResult<Map<String, JsonObject>>> getLocations(
    Collection<Item> inventoryRecords) {

    List<String> locationIds = inventoryRecords.stream()
      .map(Item::getLocationId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    String locationsQuery = CqlHelper.multipleRecordsCqlQuery(locationIds);

    return locationsStorageClient.getMany(locationsQuery, locationIds.size(), 0)
      .thenApply(response ->
        MultipleRecords.from(response, identity(), "locations"))
      .thenApply(r -> r.map(locations ->
        locations.toMap(record -> record.getString("id"))));
  }
}
