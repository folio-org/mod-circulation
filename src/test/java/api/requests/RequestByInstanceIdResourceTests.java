package api.requests;

import static java.util.Collections.emptySet;
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

import org.apache.commons.collections.map.ListOrderedMap;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.representations.RequestByInstanceIdRequest;
import org.folio.circulation.resources.RequestByInstanceIdResource;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;
import org.junit.experimental.theories.suppliers.TestedOn;

import api.support.APITests;
import api.support.builders.LocationBuilder;
import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdResourceTests extends APITests {

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

    JsonObject location3 = getLocationWithServicePoints(emptySet(), null, institutionId);

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

    ListOrderedMap locationIdItemMap = new ListOrderedMap();
    locationIdItemMap.put(item1.getLocationId(), item1);
    locationIdItemMap.put(item2.getLocationId(), item2);
    locationIdItemMap.put(item3.getLocationId(), item3);
    locationIdItemMap.put(item4.getLocationId(), item4);

    List<Item> items = RequestByInstanceIdResource.getItemsWithMatchingServicePointIds(locations,locationIdItemMap,pickupServicePointId);
    assertEquals(2, items.size());
    assertEquals(item2.getItem(), items.get(0).getItem());
    assertEquals(item2.getItemId(), items.get(0).getItemId());
    assertEquals(item2.getLocationId(), items.get(0).getLocationId());

    assertEquals(item4.getItem(), items.get(1).getItem());
    assertEquals(item4.getItemId(), items.get(1).getItemId());
    assertEquals(item4.getLocationId(), items.get(1).getLocationId());
  }

  private static JsonObject getLocationWithServicePoints(Set<UUID> servicePoints, UUID primaryServicePointId, UUID locationInstitutionId) {

    JsonObject location = new LocationBuilder()
      .forInstitution(locationInstitutionId)
      .withPrimaryServicePoint(primaryServicePointId)
      .servedBy(servicePoints)
      .create();
    location.put("id", UUID.randomUUID().toString());

    return location;
  }
}
