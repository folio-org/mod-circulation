package org.folio.circulation.infrastructure.storage.inventory;

import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.folio.circulation.support.fetching.RecordFetching.findWithMultipleCqlIndexValues;
import static org.folio.circulation.support.results.AsynchronousResultBindings.combineAfter;
import static org.folio.circulation.support.results.Result.ofAsync;
import static org.folio.circulation.support.results.Result.succeeded;
import static org.folio.circulation.support.results.ResultBinding.flatMapResult;
import static org.folio.circulation.support.results.ResultBinding.mapResult;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Institution;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.infrastructure.storage.ServicePointRepository;
import org.folio.circulation.storage.mappers.InstitutionMapper;
import org.folio.circulation.storage.mappers.LocationMapper;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.CollectionResourceClient;
import org.folio.circulation.support.FindWithMultipleCqlIndexValues;
import org.folio.circulation.support.SingleRecordFetcher;
import org.folio.circulation.support.results.Result;

import io.vertx.core.json.JsonObject;

public class LocationRepository {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final CollectionResourceClient locationsStorageClient;
  private final CollectionResourceClient institutionsStorageClient;
  private final CollectionResourceClient campusesStorageClient;
  private final CollectionResourceClient librariesStorageClient;
  private final ServicePointRepository servicePointRepository;

  private LocationRepository(CollectionResourceClient locationsStorageClient,
    CollectionResourceClient institutionsStorageClient,
    CollectionResourceClient campusesStorageClient,
    CollectionResourceClient librariesStorageClient,
    ServicePointRepository servicePointRepository) {

    this.locationsStorageClient = locationsStorageClient;
    this.institutionsStorageClient = institutionsStorageClient;
    this.campusesStorageClient = campusesStorageClient;
    this.librariesStorageClient = librariesStorageClient;
    this.servicePointRepository = servicePointRepository;
  }

  public static LocationRepository using(Clients clients,
    ServicePointRepository servicePointRepository) {

    return new LocationRepository(clients.locationsStorage(),
      clients.institutionsStorage(), clients.campusesStorage(),
      clients.librariesStorage(), servicePointRepository);
  }

  public CompletableFuture<Result<Location>> getLocation(Item item) {
    if (item == null || item.getEffectiveLocationId() == null) {
      return ofAsync(() -> null);
    }

    return fetchLocationById(item.getEffectiveLocationId())
      .thenCompose(combineAfter(this::fetchPrimaryServicePoint,
        Location::withPrimaryServicePoint))
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
      .thenApply(r -> r.map(new LocationMapper()::toDomain));
  }

  public CompletableFuture<Result<Map<String, Location>>> getItemLocations(
    Collection<Item> inventoryRecords) {

    final var locationIds = new MultipleRecords<>(inventoryRecords, inventoryRecords.size())
      .toKeys(Item::getEffectiveLocationId);

    return fetchLocations(locationIds)
      .thenApply(mapResult(records -> records.toMap(Location::getId)));
  }

  public CompletableFuture<Result<MultipleRecords<Location>>> fetchLocations(
    Set<String> locationIds) {

    final FindWithMultipleCqlIndexValues<Location> fetcher
      = findWithMultipleCqlIndexValues(locationsStorageClient, "locations",
      new LocationMapper()::toDomain);

    return fetcher.findByIds(locationIds)
      .thenCompose(this::loadLibrariesForLocations);
  }

  private CompletableFuture<Result<Location>> loadLibrary(Location location) {
    if(isNull(location) || isNull(location.getLibraryId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(librariesStorageClient, "library", response -> succeeded(null))
      .fetch(location.getLibraryId())
      .thenApply(r -> r.map(location::withLibraryRepresentation));
  }

  public CompletableFuture<Result<Location>> loadCampus(Location location) {
    if(isNull(location) || isNull(location.getCampusId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(campusesStorageClient, "campus", response -> succeeded(null))
      .fetch(location.getCampusId())
      .thenApply(r -> r.map(location::withCampusRepresentation));
  }

  public CompletableFuture<Result<Location>> loadInstitution(Location location) {
    if(isNull(location) || isNull(location.getInstitutionId())) {
      return ofAsync(() -> null);
    }

    return SingleRecordFetcher.json(institutionsStorageClient, "institution", response -> succeeded(null))
      .fetch(location.getInstitutionId())
      .thenApply(mapResult(new InstitutionMapper()::toDomain))
      .thenApply(mapResult(location::withInstitution));
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

    final Set<String> libraryIds = uniqueSet(locations, Location::getLibraryId);

    return fetcher.findByIds(libraryIds)
            .thenApply(mapResult(records -> records.toMap(library ->
                    library.getString("id"))));
  }

  public CompletableFuture<Result<Map<String, JsonObject>>> getCampuses(
    Collection<Location> locations) {

    final FindWithMultipleCqlIndexValues<JsonObject> fetcher
      = findWithMultipleCqlIndexValues(campusesStorageClient, "loccamps", identity());

    final Set<String> campusesIds = uniqueSet(locations, Location::getCampusId);

    return fetcher.findByIds(campusesIds)
      .thenApply(mapResult(records -> records.toMap(campus ->
        campus.getString("id"))));
  }

  public CompletableFuture<Result<Map<String, Institution>>> getInstitutions(
    Collection<Location> locations) {

    final FindWithMultipleCqlIndexValues<Institution> fetcher
      = findWithMultipleCqlIndexValues(institutionsStorageClient, "locinsts",
      new InstitutionMapper()::toDomain);

    final Set<String> institutionsIds = uniqueSet(locations, Location::getInstitutionId);

    return fetcher.findByIds(institutionsIds)
      .thenApply(mapResult(records -> records.toMap(Institution::getId)));
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
          .map(location -> location.withInstitution(
            institutions.getOrDefault(location.getInstitutionId(), Institution.unknown(location.getInstitutionId()))))
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

  private CompletableFuture<Result<ServicePoint>> fetchPrimaryServicePoint(Location location) {
    if (location == null || location.getPrimaryServicePointId() == null) {
      log.info("Location was not found, aborting fetching primary service point");
      return ofAsync(() -> null);
    }

    return servicePointRepository.getServicePointById(location.getPrimaryServicePointId());
  }

  private <T, R> Set<R> uniqueSet(Collection<T> collection, Function<T, R> mapper) {
    return collection.stream()
      .filter(Objects::nonNull)
      .map(mapper)
      .filter(Objects::nonNull)
      .collect(toSet());
  }
}
