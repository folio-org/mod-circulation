package org.folio.circulation.services;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.folio.circulation.domain.RequestType.PAGE;
import static org.folio.circulation.domain.representations.RequestProperties.ITEM_LOCATION_CODE;
import static org.folio.circulation.domain.representations.RequestProperties.INSTANCE_ID;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;
import static org.folio.circulation.support.results.Result.of;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.folio.circulation.support.request.RequestRelatedRepositories;
import org.folio.circulation.support.results.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ItemForTlrService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LocationRepository locationRepository;

  public static ItemForTlrService using(RequestRelatedRepositories repositories) {
    return new ItemForTlrService(repositories.getLocationRepository());
  }

  public List<Item> findAvailablePageableItems(Request request) {

    return request.getInstanceItems()
      .stream()
      .filter(item -> ItemStatus.AVAILABLE == item.getStatus())
      .filter(item -> request.getInstanceItemsRequestPolicies().get(item.getItemId()).allowsType(PAGE))
      .collect(toList());
  }

  public CompletableFuture<Result<Request>> findClosestAvailablePageableItem(Request request) {
    List<Item> availablePageableItems = findAvailablePageableItems(request);

    return refusePageRequestWhenNoAvailablePageableItemsExist(request, availablePageableItems)
      .after(items ->
        locationRepository.fetchLocationsForServicePoint(request.getPickupServicePointId())
          .thenApply(rl -> rl.map(locations -> pickClosestItem(locations, items)))
          .thenApply(ri -> ri.map(request::withItem)));
  }

  private Result<List<Item>> refusePageRequestWhenNoAvailablePageableItemsExist(Request request,
    List<Item> availablePageableItems) {

    if (availablePageableItems.isEmpty()) {
      String message = "Cannot create page TLR for this instance ID - no pageable available " +
        "items found";
      log.info("{}. Instance ID: {}", message, request.getInstanceId());
      return failedValidation(message, INSTANCE_ID, request.getInstanceId());
    }

    if (request.geItemLocationCode() == null) {
      return of(() -> availablePageableItems);
    } else {
      List<Item> finalAvailablePageableItems = availablePageableItems.stream()
        .filter(item -> item.isAtLocation(request.geItemLocationCode()))
        .toList();
      if (finalAvailablePageableItems.isEmpty()) {
        String message = "Cannot create page TLR for this instance ID - no pageable available " +
          "items found in requested location";
        log.info("{}. Instance ID: {}, Requested location code {}",
          message, request.getInstanceId(), request.geItemLocationCode());
        return failedValidation(message, ITEM_LOCATION_CODE, request.geItemLocationCode());
      }
      return of(() -> finalAvailablePageableItems);
    }
  }

  private static Item pickClosestItem(Collection<Location> requestedLocations, List<Item> availableItems) {
    Map<Location, List<Item>> availableItemsByLocation = availableItems.stream()
      .filter(item -> item.getLocation() != null)
      .collect(groupingBy(Item::getLocation));

    return findIntersection(requestedLocations, availableItemsByLocation.keySet())
      .map(location -> availableItemsByLocation.get(location).get(0))
      .orElseGet(() -> availableItems.get(0));
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
      .filter(Objects::nonNull)
      .collect(toSet());

    return available.stream()
      .filter(location -> requestedIds.contains(idExtractor.apply(location)))
      .findFirst();
  }
}
