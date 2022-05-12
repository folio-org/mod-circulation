package org.folio.circulation.services;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.Request;
import org.folio.circulation.infrastructure.storage.inventory.LocationRepository;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.results.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ItemForPageTlrService {

  private final LocationRepository locationRepository;

  public static ItemForPageTlrService using(Clients clients) {
    return new ItemForPageTlrService(LocationRepository.using(clients));
  }

  public CompletableFuture<Result<Request>> findItem(Request request) {
    List<Item> availableItems = request.getInstanceItems()
      .stream()
      .filter(item -> ItemStatus.AVAILABLE == item.getStatus())
      .collect(toList());

    if (availableItems.isEmpty()) {
      return completedFuture(failedValidation(
        "Cannot create page TLR for this instance ID - no available items found",
        INSTANCE_ID, request.getInstanceId()));
    }

    return locationRepository.fetchLocationsForServicePoint(request.getPickupServicePointId())
      .thenApply(r -> r.map(locations -> findItem(locations, availableItems)))
      .thenApply(r -> r.map(request::withItem));
  }

  private static Item findItem(Collection<Location> requestedLocations, List<Item> availableItems) {
    Map<Location, List<Item>> availableItemsByLocation = availableItems
      .stream()
      .filter(item -> item.getLocation() != null)
      .collect(groupingBy(Item::getLocation));

    return findIntersection(requestedLocations, availableItemsByLocation.keySet())
      .map(location -> availableItemsByLocation.get(location).get(0))
      .orElseGet(() -> availableItems.get(0));
  }

  private static Optional<Location> findIntersection(Collection<Location> requested,
    Collection<Location> available) {

    if (requested.isEmpty() || available.isEmpty()) {
      return Optional.empty();
    }

    return Optional.<Location>empty()
      .or(() -> findIntersection(requested, available, Location::getId))
      .or(() -> findIntersection(requested, available, Location::getLibraryId))
      .or(() -> findIntersection(requested, available, Location::getCampusId))
      .or(() -> findIntersection(requested, available, Location::getInstitutionId));
  }

  private static Optional<Location> findIntersection(Collection<Location> requested,
    Collection<Location> available, Function<Location, String> idExtractor) {

    Set<String> requestedIds = requested.stream()
      .map(idExtractor)
      .filter(Objects::nonNull)
      .collect(toSet());

    return available.stream()
      .filter(location -> requestedIds.contains(idExtractor.apply(location)))
      .findFirst();
  }
}
