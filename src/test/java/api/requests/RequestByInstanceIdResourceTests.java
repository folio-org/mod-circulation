package api.requests;

import static java.util.Collections.emptySet;
import static org.folio.circulation.resources.RequestByInstanceIdResource.rankItemsByMatchingServicePoint;
import static org.folio.circulation.resources.RequestByInstanceIdResourceTests.getJsonInstanceRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.InstanceRequestRelatedRecords;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.resources.RequestByInstanceIdResource;
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
  public void canGetOrderedAvailableItemsList()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID primaryServicePointId = servicePointsFixture.cd2().getId();
    UUID secondaryServicePointId = UUID.randomUUID();
    UUID pickupServicePointId = primaryServicePointId;
    UUID institutionId = UUID.randomUUID();

    HashSet<UUID> servicePoints1 = new HashSet<>();
    servicePoints1.add(secondaryServicePointId);
    Location location1 = getLocationWithServicePoints(servicePoints1, secondaryServicePointId, institutionId);

    //Matching item and servicePoints
    HashSet<UUID> servicePoints2 = new HashSet<>();
    servicePoints2.add(primaryServicePointId);
    servicePoints2.add(secondaryServicePointId);

    Location location2 = getLocationWithServicePoints(servicePoints2, primaryServicePointId, institutionId);

    Location location3 = getLocationWithServicePoints(emptySet(), null, institutionId);

    //Matching item and servicePoints
    HashSet<UUID> servicePoints4 = new HashSet<>();
    servicePoints4.add(primaryServicePointId);
    Location location4 = getLocationWithServicePoints(servicePoints4, primaryServicePointId, institutionId);

    UUID bookMaterialTypeId = UUID.randomUUID();
    UUID loanTypeId = UUID.randomUUID();

    Item item1 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location1.getId()))
      .create())
      .withLocation(location1);

    Item item2 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location2.getId()))
      .create())
      .withLocation(location2);

    Item item3 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location3.getId()))
      .create())
      .withLocation(location3);

    Item item4 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
      .withTemporaryLocation(UUID.fromString(location4.getId()))
      .create())
      .withLocation(location4);

    final ArrayList<Item> items = new ArrayList<>();

    items.add(item2);
    items.add(item3);
    items.add(item4);
    items.add(item1);

    InstanceRequestRelatedRecords records = new InstanceRequestRelatedRecords();
    JsonObject requestJson = getJsonInstanceRequest(pickupServicePointId);
    Result<RequestByInstanceIdRequest> request = RequestByInstanceIdRequest.from(requestJson);

    records.setUnsortedAvailableItems(items);
    records.setRequestByInstanceIdRequest(request.value());

    Result<InstanceRequestRelatedRecords> rankResult = rankItemsByMatchingServicePoint(records);

    final List<Item> orderedItems = rankResult.value().getCombineItemsList();

    assertEquals(4, orderedItems.size());

    assertEquals(item2.getItemId(), orderedItems.get(0).getItemId());
    assertEquals(item4.getItemId(), orderedItems.get(1).getItemId());
    assertEquals(item3.getItemId(), orderedItems.get(2).getItemId());
    assertEquals(item1.getItemId(), orderedItems.get(3).getItemId());
  }

  @Test
  public void canGetOrderedAvailableItemsListWithoutMatchingLocations() {

    UUID bookMaterialTypeId = UUID.randomUUID();

    UUID loanTypeId = UUID.randomUUID();

    Location location = getLocationWithServicePoints(emptySet(), null, null);

    Item item1 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create())
      .withLocation(location);

    Item item2 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create())
      .withLocation(location);

    Item item3 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create())
      .withLocation(location);

    Item item4 = Item.from(ItemExamples.basedUponSmallAngryPlanet(bookMaterialTypeId, loanTypeId)
                      .withTemporaryLocation(UUID.randomUUID()).create())
      .withLocation(location);

    //order added is important so the test deliberately add items in a certain order
    List<Item> items = new ArrayList<>();

    items.add(item3);
    items.add(item2);
    items.add(item4);
    items.add(item1);

    InstanceRequestRelatedRecords records = new InstanceRequestRelatedRecords();
    JsonObject requestJson =  getJsonInstanceRequest(UUID.randomUUID());
    Result<RequestByInstanceIdRequest> request = RequestByInstanceIdRequest.from(requestJson);

    records.setUnsortedAvailableItems(items);
    records.setRequestByInstanceIdRequest(request.value());

    Result<InstanceRequestRelatedRecords> rankResult = rankItemsByMatchingServicePoint(records);
    final List<Item> orderedItems = rankResult.value().getCombineItemsList();

    assertEquals(4, orderedItems.size());

    assertEquals(item3.getItemId(),orderedItems.get(0).getItemId());
    assertEquals(item2.getItemId(),orderedItems.get(1).getItemId());
    assertEquals(item4.getItemId(),orderedItems.get(2).getItemId());
    assertEquals(item1.getItemId(),orderedItems.get(3).getItemId());
  }

  private static Location getLocationWithServicePoints(Set<UUID> servicePoints, UUID primaryServicePointId, UUID locationInstitutionId) {

    JsonObject location = new LocationBuilder()
      .forInstitution(locationInstitutionId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(servicePoints)
      .create();

    location.put("id", UUID.randomUUID().toString());

    return Location.from(location);
  }
}
