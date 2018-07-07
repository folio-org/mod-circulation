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

import static java.util.function.Function.identity;

public class LocationRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CollectionResourceClient locationsStorageClient;

  public LocationRepository(Clients clients) {
    locationsStorageClient = clients.locationsStorage();
  }

  public CompletableFuture<HttpResult<Item>> getLocation(
    Item item) {

    return getLocation(item.getLocationId(), item.getItemId())
      .thenApply(result -> result.map(item::withLocation));
  }

  private CompletableFuture<HttpResult<JsonObject>> getLocation(
    String locationId,
    String itemId) {

    //TODO: Add functions to explicitly distinguish between fatal not found
    // and allowable not found
    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.succeeded(response.getJson());
      }
      else {
        log.warn("Could not get location {} for item {}",
          locationId, itemId);

        return HttpResult.succeeded(null);
      }
    };

    return locationsStorageClient.get(locationId)
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failed(new ServerErrorFailure(e)));
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
