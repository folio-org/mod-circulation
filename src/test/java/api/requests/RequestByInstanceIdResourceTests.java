package api.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.resources.RequestByInstanceIdResource;
import org.folio.circulation.support.Pair;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LocationBuilder;
import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdResourceTests extends APITests {

  @Test
  public void canTransformInstanceToItemRequests(){

    UUID loanTypeId = UUID.randomUUID();
    RequestByInstanceIdRequest requestByInstanceIdRequest = RequestByInstanceIdRequest.from(getJsonInstanceRequest()).value();
    List<Item > items = getItems(2, loanTypeId);

    final Result<LinkedList<JsonObject>> collectionResult = RequestByInstanceIdResource.instanceToItemRequests(requestByInstanceIdRequest, items);
    assertTrue(collectionResult.succeeded());

    Collection<JsonObject> requestRepresentations = collectionResult.value();
    assertEquals(6, requestRepresentations.size());

    int i = 0;
    int j = 0;
    Item item = items.get(j);
    for (JsonObject itemRequestJson: requestRepresentations) {
      assertEquals(item.getItemId(), itemRequestJson.getString("itemId"));
      if (i == 0)
        assertEquals(RequestType.HOLD.name(), itemRequestJson.getString("requestType"));
      if (i == 1)
        assertEquals(RequestType.RECALL.name(), itemRequestJson.getString("requestType"));
      if (i == 2)
        assertEquals(RequestType.PAGE.name(), itemRequestJson.getString("requestType"));
      i++;

      if (i > 2) {
        i = 0;
        j++;
        if (j < 2) {
          item = items.get(j);
        }
      }
    }
  }

  @Test
  public void canGetItemWithMatchingServicePointIds()
    throws InterruptedException, MalformedURLException, TimeoutException, ExecutionException {

    UUID primaryServicePointId = servicePointsFixture.cd2().getId();
    UUID secondaryServicePointId = UUID.randomUUID();
    String pickupServicePointId = primaryServicePointId.toString();
    UUID institutionId = UUID.randomUUID();

    List<Result<JsonObject>> locations = new ArrayList<>();

    HashSet<UUID> servicePoints1 = new HashSet<>();
    servicePoints1.add(secondaryServicePointId);
    JsonObject location1 = getLocationWithServicePoints(servicePoints1, secondaryServicePointId, institutionId);

    //Matching item and servicePoints
    HashSet<UUID> servicePoints2 = new HashSet<>();
    servicePoints2.add(primaryServicePointId);
    servicePoints2.add(secondaryServicePointId);

    JsonObject location2 = getLocationWithServicePoints(servicePoints2, primaryServicePointId, institutionId);

    JsonObject location3 = getLocationWithServicePoints(null, null, institutionId);

    //Matching item and servicePoints
    HashSet<UUID> servicePoints4 = new HashSet<>();
    servicePoints4.add(primaryServicePointId);
    JsonObject location4 = getLocationWithServicePoints(servicePoints4, primaryServicePointId, institutionId);

    locations.add(Result.succeeded(location1));
    locations.add(Result.succeeded(location2));
    locations.add(Result.succeeded(location3));
    locations.add(Result.succeeded(location4));

    UUID bookMaterialTypeId = UUID.randomUUID();
    UUID loanTypeId = UUID.randomUUID();
    Item item1 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location1.getString("id")))
      .create());
    Item item2 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location2.getString("id")))
      .create());
    Item item3 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location3.getString("id")))
      .create());
    Item item4 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location4.getString("id")))
      .create());

    LinkedList<Pair> locationIdItemMap = new LinkedList<>();
    locationIdItemMap.add(new Pair(item1.getLocationId(), item1));
    locationIdItemMap.add(new Pair(item2.getLocationId(), item2));
    locationIdItemMap.add(new Pair(item3.getLocationId(), item3));
    locationIdItemMap.add(new Pair(item4.getLocationId(), item4));

    List<Item> items = RequestByInstanceIdResource.getItemsWithMatchingServicePointIds(locations,locationIdItemMap,pickupServicePointId);
    assertEquals(2, items.size());
    assertEquals(item2.getItem(), items.get(0).getItem());
    assertEquals(item2.getItemId(), items.get(0).getItemId());
    assertEquals(item2.getLocationId(), items.get(0).getLocationId());

    assertEquals(item4.getItem(), items.get(1).getItem());
    assertEquals(item4.getItemId(), items.get(1).getItemId());
    assertEquals(item4.getLocationId(), items.get(1).getLocationId());
  }

  @Test
  public void canGetOrderedAvailableItemsList(){

    UUID bookMaterialTypeId = UUID.randomUUID();
    UUID loanTypeId = UUID.randomUUID();
    Item item1 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId).withTemporaryLocation(UUID.randomUUID()).create());
    Item item2 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId).withTemporaryLocation(UUID.randomUUID()).create());
    Item item3 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId).withTemporaryLocation(UUID.randomUUID()).create());
    Item item4 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId).withTemporaryLocation(UUID.randomUUID()).create());

    LinkedList<Pair> locationIdItemMap = new LinkedList<>();
    locationIdItemMap.add(new Pair(item1.getLocationId(), item1));
    locationIdItemMap.add(new Pair(item2.getLocationId(), item2));
    locationIdItemMap.add(new Pair(item3.getLocationId(), item3));
    locationIdItemMap.add(new Pair(item4.getLocationId(), item4));

    List<Item> matchingItemsList = new LinkedList<>();
    matchingItemsList.add(item4);
    matchingItemsList.add(item1);

    List<Item> ordedItems = RequestByInstanceIdResource.getOrderedAvailableItemsList(matchingItemsList, locationIdItemMap);
    assertEquals(4, ordedItems.size());
    assertEquals(item4.getItemId(),ordedItems.get(0).getItemId());
    assertEquals(item1.getItemId(),ordedItems.get(1).getItemId());
    assertEquals(item2.getItemId(),ordedItems.get(2).getItemId());
    assertEquals(item3.getItemId(),ordedItems.get(3).getItemId());
  }

  @Test
  public void canGetOrderedAvailableItemsListWithoutMatchingLocations(){

    UUID bookMaterialTypeId = UUID.randomUUID();
    UUID loanTypeId = UUID.randomUUID();
    Item item1 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create());
    Item item2 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create());
    Item item3 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create());
    Item item4 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create());

    //order added is important so the test deliberately add items in a certain order
    LinkedList<Pair> locationIdItemMap = new LinkedList<>();
    locationIdItemMap.add(new Pair(item3.getLocationId(), item3));
    locationIdItemMap.add(new Pair(item2.getLocationId(), item2));
    locationIdItemMap.add(new Pair(item4.getLocationId(), item4));
    locationIdItemMap.add(new Pair(item1.getLocationId(), item1));

    List<Item> matchingItemsList = new LinkedList<>();

    List<Item> ordedItems = RequestByInstanceIdResource.getOrderedAvailableItemsList(matchingItemsList, locationIdItemMap);
    assertEquals(4, ordedItems.size());
    assertEquals(item3.getItemId(),ordedItems.get(0).getItemId());
    assertEquals(item2.getItemId(),ordedItems.get(1).getItemId());
    assertEquals(item4.getItemId(),ordedItems.get(2).getItemId());
    assertEquals(item1.getItemId(),ordedItems.get(3).getItemId());
  }

  private static JsonObject getJsonInstanceRequest(){
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put("instanceId", UUID.randomUUID().toString());
    instanceRequest.put("requestDate", requestDate.toString(ISODateTimeFormat.dateTime()));
    instanceRequest.put("requesterId", UUID.randomUUID().toString());
    instanceRequest.put("pickupServicePointId", UUID.randomUUID().toString());
    instanceRequest.put("fulfilmentPreference", "Hold Shelf");
    instanceRequest.put("requestExpirationDate",requestExpirationDate.toString(ISODateTimeFormat.dateTime()));

    return instanceRequest;
  }

  private static List<Item> getItems(int totalItems, UUID loanTypeId){
    LinkedList<Item> items = new LinkedList<>();
    for (int i = 0; i< totalItems; i++){
      JsonObject itemJsonObject = ItemExamples.basedUponSmallAngryPlanet(UUID.randomUUID(), loanTypeId).create();
      items.add(Item.from(itemJsonObject));
    }
    return items;
  }

  private static JsonObject getLocationWithServicePoints(HashSet<UUID> servicePoints, UUID primaryServicePointId,  UUID locationInstitutionId) {

    JsonObject location = new LocationBuilder()
      .forInstitution(locationInstitutionId)
      .withPrimaryServicePoint(primaryServicePointId)
      .withServicePoints(servicePoints)
      .create();
    location.put("id", UUID.randomUUID().toString());

    return location;
  }
}
