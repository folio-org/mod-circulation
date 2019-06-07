package org.folio.circulation.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdResourceTests {
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

  private static List<Item> getItems(int totalItems, UUID loanTypeId){
    LinkedList<Item> items = new LinkedList<>();
    for (int i = 0; i< totalItems; i++){
      JsonObject itemJsonObject = ItemExamples.basedUponSmallAngryPlanet(UUID.randomUUID(), loanTypeId).create();
      items.add(Item.from(itemJsonObject));
    }
    return items;
  }
}
