package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.Result.ofAsync;
import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.support.ResultBinding.mapResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.MultipleRecordFetcher;
import org.folio.circulation.support.Result;
import org.folio.circulation.support.SingleRecordFetcher;

public class LocationRepository {

  private CollectionResourceClient locationsStorageClient;
  private CollectionResourceClient institutionsStorageClient;
  private CollectionResourceClient campusesStorageClient;
  private CollectionResourceClient librariesStorageClient;

  private LocationRepository(CollectionResourceClient locationsStorageClient,
                             CollectionResourceClient institutionsStorageClient,
                             CollectionResourceClient campusesStorageClient,
                             CollectionResourceClient librariesStorageClient) {

    this.locationsStorageClient = locationsStorageClient;
    this.institutionsStorageClient = institutionsStorageClient;
    this.campusesStorageClient = campusesStorageClient;
    this.librariesStorageClient = librariesStorageClient;
  }

  public static LocationRepository using(Clients clients) {
    return new LocationRepository(
      clients.locationsStorage(),
      clients.institutionsStorage(),
      clients.campusesStorage(),
      clients.librariesStorage()
    );
  }

  public CompletableFuture<Result<Location>> getLocation(Item item) {
    if(isNull(item) || isNull(item.getLocationId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(locationsStorageClient, "location",
      response -> succeeded(null))
      .fetch(item.getLocationId())
      .thenApply(r -> r.map(Location::from))
      .thenCompose(r -> r.after(this::loadLibrary))
      .thenCompose(r -> r.after(this::loadCampus))
      .thenCompose(r -> r.after(this::loadInstitution));
  }

  public CompletableFuture<Result<Map<String, Location>>> getLocations(
    Collection<Item> inventoryRecords) {

    List<String> locationIds = inventoryRecords.stream()
      .map(Item::getLocationId)
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toList());

    final MultipleRecordFetcher<Location> fetcher = new MultipleRecordFetcher<>(
      locationsStorageClient, "locations", Location::from);

    return fetcher.findByIds(locationIds)
      .thenApply(mapResult(sds -> sds.toMap(Location::getId)));
  }

  private CompletableFuture<Result<Location>> loadLibrary(Location location) {
    if(isNull(location) || isNull(location.getLibraryId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(librariesStorageClient, "library", response -> succeeded(null))
      .fetch(location.getLibraryId())
      .thenApply(r -> r.map(location::withLibraryRepresentation));
  }

  private CompletableFuture<Result<Location>> loadCampus(Location location) {
    if(isNull(location) || isNull(location.getCampusId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(campusesStorageClient, "campus", response -> succeeded(null))
      .fetch(location.getCampusId())
      .thenApply(r -> r.map(location::withCampusRepresentation));
  }

  private CompletableFuture<Result<Location>> loadInstitution(Location location) {
    if(isNull(location) || isNull(location.getInstitutionId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(institutionsStorageClient, "institution", response -> succeeded(null))
      .fetch(location.getInstitutionId())
      .thenApply(r -> r.map(location::withInstitutionRepresentation));
  }
}
