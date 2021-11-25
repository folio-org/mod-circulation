package org.folio.circulation.domain;

import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.storage.mappers.ServicePointMapper;
import org.junit.jupiter.api.Test;

import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.builders.ServicePointBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

class RequestRepresentationTests {
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ADDRESS_ID = UUID.randomUUID();
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();

  @Test
  void testExtendedRepresentation() {
    RequestRepresentation requestRepresentation = new RequestRepresentation();
    Request request = createMockRequest();
    JsonObject extendedRepresentation = requestRepresentation.extendedRepresentation(request);

    assertThat("Extended representation should have a pickup service point",
      extendedRepresentation.containsKey("pickupServicePoint"), is(true));

    assertThat("Extended representation should have a delivery address",
      extendedRepresentation.containsKey("deliveryAddress"), is(true));
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

    final ServicePointBuilder servicePointBuilder = new ServicePointBuilder("Circ Desk", "cd1", "Circulation Desk")
      .withId(SERVICE_POINT_ID)
      .withPickupLocation(TRUE);

    final var servicePoint = new ServicePointMapper().toDomain(servicePointBuilder.create());

    JsonObject requestJsonObject = new RequestBuilder()
      .recall()
      .withId(REQUEST_ID)
      .withRequestDate(requestDate)
      .withItemId(ITEM_ID)
      .withRequesterId(requesterId)
      .fulfilToHoldShelf()
      .withPickupServicePointId(SERVICE_POINT_ID)
      .withRequestExpiration(LocalDate.of(2017, 7, 30))
      .deliverToAddress(ADDRESS_ID)
      .create();

    return Request.from(requestJsonObject)
      .withRequester(requester)
      .withPickupServicePoint(servicePoint);
  }
}
