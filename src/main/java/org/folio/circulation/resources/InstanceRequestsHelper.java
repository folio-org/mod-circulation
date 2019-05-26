package org.folio.circulation.resources;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LocationRepository;
import org.folio.circulation.support.Result;

import io.vertx.core.json.JsonObject;

public class InstanceRequestsHelper {

  public static CompletableFuture<Collection<Item>> findItemsWithMatchingServicePointId(String pickupServicePointId,
                                                                           Collection<Item> items,
                                                                           LocationRepository locationRepository) {
    HashMap<String, Item> locationItemMap = new HashMap<>();
    return getLocationFutures(items, locationRepository, locationItemMap)
      .thenApply(locations -> {
        //Use the matchingLocationIds list to get the items from locationItemMap
        List<Item> matchingItemsList = getItemWithMatchingServicePointIds(locations, locationItemMap, pickupServicePointId);
        return getOrderedAvailableItemsList(matchingItemsList, locationItemMap);
      });
  }


  public static CompletableFuture<Collection<Result<JsonObject>>> getLocationFutures(Collection<Item> items,
                                                                               LocationRepository locationRepository,
                                                                               HashMap<String, Item> locationItemMap) {
    //for a given location ID, find all the service points.
    //if there's a matching service point ID, pick that item
    Collection<CompletableFuture<Result<JsonObject>>> locationFutures = new ArrayList<>();

    //Find locations of all items
    for (Item item : items) {
      locationItemMap.put(item.getLocationId(), item);
      final CompletableFuture<Result<JsonObject>> locationFuture = locationRepository.getLocation(item);
      locationFutures.add(locationFuture);
    }

    //Collect the location objects once they come back
    CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                                            locationFutures.toArray(
                                              new CompletableFuture[locationFutures.size()]));

    return allFutures.thenApply(x -> locationFutures.stream()
      .map(CompletableFuture::join)
      .collect(Collectors.toList()));
  }

  public static List<Item> getOrderedAvailableItemsList(List<Item> matchingItemsList, HashMap<String, Item> locationItemMap) {

    //Compose the final list of Items with the matchingItems (items that has matching service pointID) on top.
    List<Item> finalOrderedList = new LinkedList<>();
    finalOrderedList.addAll(matchingItemsList);

    //loop through all the items in the maps and add the remaining ones, those that have not been added to the bottom.
    Collection<Item> locationItemMapValues = locationItemMap.values();
    for (Item anItem : locationItemMapValues) {
      if (!matchingItemsList.contains(anItem)) {
        finalOrderedList.add(anItem);
      }
    }

    return finalOrderedList;
  }

  public static List<Item> getItemWithMatchingServicePointIds(Collection<Result<JsonObject>> locations,
                                                              HashMap<String, Item> locationItemMap,
                                                              String pickupServicePointId) {
    // iterate through all locations to find the location that has a matching service point ID
    List<String> matchingLocationIds = new LinkedList<>();

    for (Result<JsonObject> locationResult : locations) {
      JsonObject location = locationResult.value();
      if (location != null) {
        //each location has multiple service points, so have to get them all
        List<String> servicePointIds = location.getJsonArray("servicePointIds").getList();
        if (servicePointIds != null && !servicePointIds.isEmpty()) {
          if (servicePointIds.stream()
            .anyMatch(servicePointId -> servicePointId.equals(pickupServicePointId))) {
            //found a match and add it to the matching location IDs list, as there could be multiple items available at one location
            matchingLocationIds.add(location.getString("id"));
            break;
          }
        }
      }
    }
    //Use the matchingLocationIds list to get the items from locationItemMap
    return matchingLocationIds
              .stream()
              .map(locationItemMap::get)
              .collect(Collectors.toList());
  }


  public static Collection<JsonObject> instanceToItemRequestObjects(JsonObject instanceRequest, Collection<Item> items) {
    return items.stream()
      .map( item -> {
        JsonObject itemRequest = instanceRequest.copy();
        itemRequest.put("itemId", item.getItemId());
        itemRequest.remove("instanceId");
        return itemRequest;
      })
      .collect(Collectors.toList());
  }
}
