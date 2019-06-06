package api.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.resources.RequestByInstanceIdResource;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdResourceUnitTests {
  @Test
  public void canTransformInstanceToItemRequests(){

    UUID loanTypeId = UUID.randomUUID();
    RequestByInstanceIdRequest requestByInstanceIdRequest = RequestByInstanceIdRequest.from(getJsonInstanceRequest(null)).value();
    List<Item > items = getItems(2, loanTypeId);

    InstanceRequestRelatedRecords records = new InstanceRequestRelatedRecords();
    records.setSortedAvailableItems(items);
    records.setRequestByInstanceIdRequest(requestByInstanceIdRequest);

    final Result<LinkedList<JsonObject>> collectionResult = RequestByInstanceIdResource.instanceToItemRequests(records);
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

  public static JsonObject getJsonInstanceRequest(UUID pickupServicePointId) {
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    JsonObject instanceRequest = new JsonObject();
    instanceRequest.put("instanceId", UUID.randomUUID().toString());
    instanceRequest.put("requestDate", requestDate.toString(ISODateTimeFormat.dateTime()));
    instanceRequest.put("requesterId", UUID.randomUUID().toString());
    instanceRequest.put("pickupServicePointId", pickupServicePointId == null ? UUID.randomUUID().toString() : pickupServicePointId.toString());
    instanceRequest.put("fulfilmentPreference", "Hold Shelf");
    instanceRequest.put("requestExpirationDate",requestExpirationDate.toString(ISODateTimeFormat.dateTime()));

    return instanceRequest;
  }

  @Test
  public void canSortMapForItems(){

    Collection<Item> items = getItems(6, UUID.randomUUID());
    Map<Item, Integer> itemQueueSizeMap = new HashMap<>();

    itemQueueSizeMap.put(((List<Item>) items).get(0), 4);
    itemQueueSizeMap.put(((List<Item>) items).get(1), 3);
    itemQueueSizeMap.put(((List<Item>) items).get(2), 7);
    itemQueueSizeMap.put(((List<Item>) items).get(3), 7);
    itemQueueSizeMap.put(((List<Item>) items).get(4), 1);
    itemQueueSizeMap.put(((List<Item>) items).get(5), 8);

    final Map<Item, Integer> sortedItemsMap = RequestByInstanceIdResource.sortMap(itemQueueSizeMap);
    final List<Integer> sortedValues = new ArrayList<>(sortedItemsMap.values());
    final Item[] sortedKeys = sortedItemsMap.keySet().toArray(new Item[sortedItemsMap.size()]);

    assertEquals(1, sortedValues.get(0).intValue());
    assertEquals(((List<Item>) items).get(4).getItemId(), sortedKeys[0].getItemId());

    assertEquals(3, sortedValues.get(1).intValue());
    assertEquals(((List<Item>) items).get(1).getItemId(), sortedKeys[1].getItemId());

    assertEquals(4, sortedValues.get(2).intValue());
    assertEquals(((List<Item>) items).get(0).getItemId(), sortedKeys[2].getItemId());

    assertEquals(8, sortedValues.get(5).intValue());
    assertEquals(((List<Item>) items).get(5).getItemId(), sortedKeys[5].getItemId());

    //for the ones that are tied, there isn't a deterministic way to find out which entry will be in front of the other
    String item2Id = ((List<Item>) items).get(2).getItemId();
    assertTrue((7 == sortedValues.get(3)) || (7 == sortedValues.get(4)));
    assertTrue(item2Id.equals(sortedKeys[3].getItemId()) || item2Id.equals(sortedKeys[4].getItemId()));

    String item3Id = ((List<Item>) items).get(3).getItemId();
    assertTrue((7 == sortedValues.get(3)) || (7 == sortedValues.get(4)));
    assertTrue(item3Id.equals(sortedKeys[3].getItemId()) || item3Id.equals(sortedKeys[4].getItemId()));
  }

  private static List<Item> getItems(int totalItems, UUID loanTypeId){
    LinkedList<Item> items = new LinkedList<>();
    for (int i = 0; i< totalItems; i++){
      JsonObject itemJsonObject = ItemExamples.basedUponSmallAngryPlanet(UUID.randomUUID(), loanTypeId).create();
      items.add(Item.from(itemJsonObject));
    }
    return items;
  }
}
