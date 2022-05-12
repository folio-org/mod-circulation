package org.folio.circulation.services;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LocationRepository locationRepository;

  public static ItemForPageTlrService using(Clients clients) {
    return new ItemForPageTlrService(LocationRepository.using(clients));
  }

  public CompletableFuture<Result<Request>> findItem(Request request) {
    return locationRepository.fetchLocationsForServicePoint(request.getPickupServicePointId())
      .thenApply(r -> r.next(locations -> findItem(request, locations)));
  }

  private static Result<Request> findItem(Request request, Collection<Location> requestedLocations) {
    Map<Location, List<Item>> availableItemsByLocation = request.getInstanceItems()
      .stream()
      .filter(item -> ItemStatus.AVAILABLE == item.getStatus())
      .filter(item -> item.getLocation() != null)
      .collect(groupingBy(Item::getLocation));

    return findIntersection(requestedLocations, availableItemsByLocation.keySet())
      .map(location -> availableItemsByLocation.get(location).get(0))
      .map(request::withItem)
      .map(Result::succeeded)
      .orElseGet(() -> failedValidation(
        "Cannot create page TLR for this instance ID - no available items found",
        INSTANCE_ID, request.getInstanceId()));
  }

  private static Optional<Location> findIntersection(Collection<Location> requested,
    Collection<Location> available) {

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
      .collect(toSet());

    return available.stream()
      .filter(location -> requestedIds.contains(idExtractor.apply(location)))
      .findFirst();
  }
}
