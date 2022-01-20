package org.folio.circulation.domain;

import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.storage.mappers.InstanceMapper;
import org.junit.jupiter.api.Test;

import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

class RequestRepresentationTests {
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ADDRESS_ID = UUID.randomUUID();
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID INSTANCE_ID = UUID.randomUUID();
  private static final String EDITIONS = "editions";
  private static final String PUBLICATION = "publication";
  private static final String INSTANCE = "instance";
  private static final String ID = "id";

  @Test
  void testExtendedRepresentation() {
    RequestRepresentation requestRepresentation = new RequestRepresentation();
    Request request = createMockRequest();
    JsonObject extendedRepresentation = requestRepresentation.extendedRepresentation(request);

    assertThat("Extended representation should have a pickup service point",
      extendedRepresentation.containsKey("pickupServicePoint"), is(true));

    assertThat("Extended representation should have a delivery address",
      extendedRepresentation.containsKey("deliveryAddress"), is(true));

    assertThat("Extended representation should have an instance",
      extendedRepresentation.containsKey(INSTANCE), is(true));

    JsonObject instance = extendedRepresentation.getJsonObject(INSTANCE);

    assertTrue(instance.containsKey(EDITIONS));
    assertTrue(instance.containsKey(PUBLICATION));
    assertEquals("First American Edition", instance.getJsonArray(EDITIONS).getString(0));
    JsonObject publication = instance.getJsonArray(PUBLICATION).getJsonObject(0);
    assertEquals("fake publisher", publication.getString("publisher"));
    assertEquals("fake place", publication.getString("place"));
    assertEquals("2016", publication.getString("dateOfPublication"));

  }

  @Test
  void testStoredRequest() {
    Request request = createMockRequest();

    JsonObject extendedRepresentation
      = new RequestRepresentation().extendedRepresentation(request);

    assertThat("Extended representation should have a pickup service point",
      extendedRepresentation.containsKey("pickupServicePoint"), is(true));

    assertThat("Extended representation should have a delivery address",
      extendedRepresentation.containsKey("deliveryAddress"), is(true));

    JsonObject storedRepresentation = new StoredRequestRepresentation()
      .storedRequest(Request.from(extendedRepresentation));

    assertThat("Stored representation should not have a delivery address",
      storedRepresentation.containsKey("deliveryAddress"), is(false));
  }

  private Request createMockRequest() {
    final UserBuilder userBuilder = new UserBuilder()
      .withName("Test", "User")
      .withBarcode("764523186496")
      .withAddress(
        new Address(ADDRESS_ID,
          "Fake first address line",
          "Fake second address line",
          "Fake city",
          "Fake region",
          "Fake postal code",
          "Fake country code"));

    final User requester = new User(userBuilder.create());

    final UUID requesterId = UUID.fromString(requester.getId());

    final ZonedDateTime requestDate = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);

    final var servicePoint
      = new ServicePoint(SERVICE_POINT_ID.toString(), "Circ Desk", "cd1", true,
      "Circulation Desk", null, null, null);

    JsonObject instanceRepresentation = new JsonObject();
    write(instanceRepresentation, EDITIONS, new JsonArray().add("First American Edition"));
    JsonObject publication = new JsonObject();
    publication.put("publisher", "fake publisher");
    publication.put("place", "fake place");
    publication.put("dateOfPublication", "2016");
    write(instanceRepresentation, PUBLICATION, new JsonArray().add(publication));
    write(instanceRepresentation, ID, UUID.randomUUID().toString());

    Instance instance = new InstanceMapper().toDomain(instanceRepresentation);

    final Item item = Item.from(new JsonObject()).withInstance(instance);

    JsonObject requestJsonObject = new RequestBuilder()
      .recall()
      .withId(REQUEST_ID)
      .withInstanceId(INSTANCE_ID)
      .withRequestDate(requestDate)
      .itemRequestLevel()
      .withItemId(ITEM_ID)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withPickupServicePointId(SERVICE_POINT_ID)
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .deliverToAddress(ADDRESS_ID)
      .create();

    return Request.from(requestJsonObject)
      .withInstance(instance)
      .withRequester(requester)
      .withPickupServicePoint(servicePoint)
      .withItem(item);
  }
}
