package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.HttpResult;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class LocationRepository {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private CollectionResourceClient locationsStorageClient;

  public LocationRepository(Clients clients) {
    locationsStorageClient = clients.locationsStorage();
  }

  public CompletableFuture<HttpResult<LoanAndRelatedRecords>> getLocation(
    LoanAndRelatedRecords relatedRecords) {

    //Cannot find location for unknown item
    if(relatedRecords.getInventoryRecords().item == null) {
      return CompletableFuture.completedFuture(HttpResult.success(relatedRecords));
    }

    //Cannot find location for unknown holding
    if(relatedRecords.getInventoryRecords().holding == null) {
      return CompletableFuture.completedFuture(HttpResult.success(relatedRecords));
    }

    final String locationId = LoanValidation.determineLocationIdForItem(
      relatedRecords.getInventoryRecords().item, relatedRecords.getInventoryRecords().holding);

    return getLocation(locationId,
      relatedRecords.getInventoryRecords().item.getString("id"))
      .thenApply(result -> result.map(relatedRecords::withLocation));
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
}
