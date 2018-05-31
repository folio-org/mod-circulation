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

public class LocationRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CollectionResourceClient locationsStorageClient;

  public LocationRepository(Clients clients) {
    locationsStorageClient = clients.locationsStorage();
  }

  public CompletableFuture<HttpResult<InventoryRecords>> getLocation(
    InventoryRecords inventoryRecords) {

    return getLocation(inventoryRecords.getLocationId(), inventoryRecords.getItemId())
      .thenApply(result -> result.map(inventoryRecords::withLocation));
  }

  private CompletableFuture<HttpResult<JsonObject>> getLocation(
    String locationId,
    String itemId) {

    CompletableFuture<Response> getLocationCompleted = new CompletableFuture<>();

    locationsStorageClient.get(locationId, getLocationCompleted::complete);

    //TODO: Add functions to explicitly distinguish between fatal not found
    // and allowable not found
    final Function<Response, HttpResult<JsonObject>> mapResponse = response -> {
      if(response != null && response.getStatusCode() == 200) {
        return HttpResult.success(response.getJson());
      }
      else {
        log.warn("Could not get location {} for item {}",
          locationId, itemId);

        return HttpResult.success(null);
      }
    };

    return getLocationCompleted
      .thenApply(mapResponse)
      .exceptionally(e -> HttpResult.failure(new ServerErrorFailure(e)));
  }

  public CompletableFuture<HttpResult<Map<String, JsonObject>>> getLocations(
    Collection<InventoryRecords> inventoryRecords) {

    List<String> locationIds = inventoryRecords.stream()
      .map(InventoryRecords::getLocationId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    CompletableFuture<Response> locationsFetched = new CompletableFuture<>();

    String locationsQuery = CqlHelper.multipleRecordsCqlQuery(locationIds);

    locationsStorageClient.getMany(locationsQuery, locationIds.size(), 0,
      locationsFetched::complete);

    return locationsFetched
      .thenApply(response -> {
        if(response.getStatusCode() != 200) {
          return HttpResult.failure(new ServerErrorFailure(
            String.format("Locations request (%s) failed %s: %s",
              locationsQuery, response.getStatusCode(),
              response.getBody())));
        }

        List<JsonObject> locations = JsonArrayHelper.toList(
          response.getJson().getJsonArray("locations"));

        return HttpResult.success(locations.stream().collect(
          Collectors.toMap(l -> l.getString("id"),
            Function.identity())));
      });
  }
}
