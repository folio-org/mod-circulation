package org.folio.circulation.domain;

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

  public LocationRepository(CollectionResourceClient locationsStorageClient,
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
    return SingleRecordFetcher.json(locationsStorageClient, "locations",
      response -> succeeded(null))
      .fetch(item.getLocationId())
      .thenApply(r -> r.map(Location::from));
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

  public CompletableFuture<Result<LoanAndRelatedRecords>> loadLocation(LoanAndRelatedRecords records) {
    return loadLocation(records.getLoan().getItem())
      .thenApply(r -> r.map(records::withItem));
  }

  public CompletableFuture<Result<RequestAndRelatedRecords>> loadLocation(RequestAndRelatedRecords records) {
    return loadLocation(records.getRequest().getItem())
      .thenApply(r -> r.map(records::withItem));
  }

  public CompletableFuture<Result<CheckInProcessRecords>> loadLocation(CheckInProcessRecords records) {
    return loadLocation(records.getItem())
      .thenApply(r -> r.map(records::withItem));
  }

  private CompletableFuture<Result<Item>> loadLocation(Item item) {
    return SingleRecordFetcher.json(locationsStorageClient, "location", response -> succeeded(null))
      .fetch(item.getLocationId())
      .thenApply(r -> r.map(Location::from))
      .thenCompose(this::loadInstitution)
      .thenCompose(this::loadCampus)
      .thenCompose(this::loadLibrary)
      .thenApply(r -> r.map(item::withLocation));
  }

  private CompletableFuture<Result<Location>> loadInstitution(Result<Location> result) {
    return result.after(location -> SingleRecordFetcher
      .json(institutionsStorageClient, "institution", response -> succeeded(null))
      .fetch(location.getInstitutionId())
      .thenApply(r -> r.map(location::withInstitutionRepresentation)));
  }

  private CompletableFuture<Result<Location>> loadCampus(Result<Location> result) {
    return result.after(location -> SingleRecordFetcher
      .json(campusesStorageClient, "campus", response -> succeeded(null))
      .fetch(location.getCampusId())
      .thenApply(r -> r.map(location::withCampusRepresentation)));
  }

  private CompletableFuture<Result<Location>> loadLibrary(Result<Location> result) {
    return result.after(location -> SingleRecordFetcher
      .json(librariesStorageClient, "library", response -> succeeded(null))
      .fetch(location.getLibraryId())
      .thenApply(r -> r.map(location::withLibraryRepresentation)));
  }
}
