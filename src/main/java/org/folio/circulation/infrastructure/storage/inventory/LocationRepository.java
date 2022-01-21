package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;
import static org.folio.circulation.support.utils.CollectionUtil.nonNullUniqueSetOf;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LocationRepository {
  private final CollectionResourceClient locationsStorageClient;
  private final CollectionResourceClient institutionsStorageClient;
  private final CollectionResourceClient campusesStorageClient;
  private final CollectionResourceClient librariesStorageClient;

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

    return fetchLocationById(item.getLocationId())
      .thenCompose(r -> r.after(this::loadLibrary))
      .thenCompose(r -> r.after(this::loadCampus))
      .thenCompose(r -> r.after(this::loadInstitution));
  }

  public CompletableFuture<Result<Location>> fetchLocationById(String id) {
    if (isBlank(id)) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(locationsStorageClient, "location",
      response -> succeeded(null))
      .fetch(id)
      .thenApply(r -> r.map(Location::from));
  }

  public CompletableFuture<Result<Map<String, Location>>> getAllItemLocations(
    Collection<Item> inventoryRecords) {

    return getItemLocations(
      inventoryRecords, List.of(Item::getLocationId, Item::getPermanentLocationId));
  }

  public CompletableFuture<Result<Map<String, Location>>> getItemLocations(
    Collection<Item> inventoryRecords, List<Function<Item, String>> locationIdMappers) {

    final Set<String> locationIds = inventoryRecords.stream()
      .flatMap(item -> mapItemToStrings(item, locationIdMappers))
      .filter(StringUtils::isNotBlank)
      .collect(Collectors.toSet());

    final FindWithMultipleCqlIndexValues<Location> fetcher
      = findWithMultipleCqlIndexValues(locationsStorageClient, "locations",
      Location::from);

    return fetcher.findByIds(locationIds)
      .thenCompose(this::loadLibrariesForLocations)
      .thenApply(mapResult(records -> records.toMap(Location::getId)));
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

  private CompletableFuture<Result<MultipleRecords<Location>>> loadLibrariesForLocations(
          Result<MultipleRecords<Location>> multipleRecordsResult) {

    return multipleRecordsResult.combineAfter(
      locations -> getLibraries(locations.getRecords()), (locations, libraries) ->
        locations.mapRecords(location -> location.withLibraryRepresentation(
          libraries.getOrDefault(location.getLibraryId(),null))));

  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getLibraries(
          Collection<Location> locations) {

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = findWithMultipleCqlIndexValues(librariesStorageClient, "loclibs", identity());

    final Set<String> libraryIds = nonNullUniqueSetOf(locations, Location::getLibraryId);

    return fetcher.findByIds(libraryIds)
            .thenApply(mapResult(records -> records.toMap(library ->
                    library.getString("id"))));
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getCampuses(
    Collection<Location> locations) {

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = findWithMultipleCqlIndexValues(campusesStorageClient, "loccamps", identity());

    final Set<String> campusesIds = nonNullUniqueSetOf(locations, Location::getCampusId);

    return fetcher.findByIds(campusesIds)
      .thenApply(mapResult(records -> records.toMap(campus ->
        campus.getString("id"))));
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getInstitutions(
    Collection<Location> locations) {

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = findWithMultipleCqlIndexValues(institutionsStorageClient, "locinsts", identity());

    final Set<String> institutionsIds = nonNullUniqueSetOf(locations, Location::getInstitutionId);

    return fetcher.findByIds(institutionsIds)
      .thenApply(mapResult(records -> records.toMap(institution ->
        institution.getString("id"))));
  }

  public CompletableFuture<Result<Collection<Location>>> fetchLibraries(Collection<Location> locations) {
    return getLibraries(locations)
      .thenApply(flatMapResult(libraries -> succeeded(
        locations.stream()
          .map(location -> location.withLibraryRepresentation(
            libraries.getOrDefault(location.getLibraryId(), null)))
          .collect(toSet()))));
  }

  public CompletableFuture<Result<Collection<Location>>> fetchInstitutions(Collection<Location> locations) {
    return getInstitutions(locations)
      .thenApply(flatMapResult(institutions -> succeeded(
        locations.stream()
          .map(location -> location.withInstitutionRepresentation(
            institutions.getOrDefault(location.getInstitutionId(), null)))
          .collect(toSet()))));
  }

  public CompletableFuture<Result<Collection<Location>>> fetchCampuses(Collection<Location> locations) {
    return getCampuses(locations)
      .thenApply(flatMapResult(campuses -> succeeded(
        locations.stream()
          .map(location -> location.withCampusRepresentation(
            campuses.getOrDefault(location.getCampusId(), null)))
          .collect(toSet()))));
  }

  private Stream<String> mapItemToStrings(Item item,
    List<Function<Item, String>> mappers) {

    return mappers.stream()
      .map(mapper -> mapper.apply(item));
  }
}
