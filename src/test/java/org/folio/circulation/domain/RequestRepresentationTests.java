package org.folio.circulation.domain;

import static java.time.ZoneOffset.UTC;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.builders.Address;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import io.vertx.core.json.JsonObject;

class RequestRepresentationTests {
  private static final UUID REQUEST_ID = UUID.randomUUID();
  private static final UUID ITEM_ID = UUID.randomUUID();
  private static final UUID ADDRESS_ID = UUID.randomUUID();
  private static final UUID SERVICE_POINT_ID = UUID.randomUUID();
  private static final UUID INSTANCE_ID = UUID.randomUUID();
  private static final UUID IDENTIFIER_ID = UUID.randomUUID();
  private static final String EDITIONS = "editions";
  private static final String PUBLICATION = "publication";
  private static final String IDENTIFIERS = "identifiers";
  private static final String INSTANCE = "instance";
  private static final String IDENTIFIER_VALUE = "identifier-value";
  private static final String SERVICE_POINT_NAME_VALUE = "Circ Desk";
  private static final String CALL_NUMBER_VALUE = "call number";
  private static final String CALL_NUMBER_PREFIX_VALUE = "call number prefix";
  private static final String CALL_NUMBER_SUFFIX_VALUE = "call number suffix";
  private static final String SHELVING_ORDER_VALUE = "shelving order";

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
    assertTrue(instance.containsKey(IDENTIFIERS));
    assertEquals("First American Edition", instance.getJsonArray(EDITIONS).getString(0));

    JsonObject publication = instance.getJsonArray(PUBLICATION).getJsonObject(0);
    assertEquals("fake publisher", publication.getString("publisher"));
    assertEquals("fake place", publication.getString("place"));
    assertEquals("2016", publication.getString("dateOfPublication"));

    JsonObject identifier = instance.getJsonArray(IDENTIFIERS).getJsonObject(0);
    assertEquals(IDENTIFIER_VALUE, identifier.getString("value"));
    assertEquals(IDENTIFIER_ID.toString(), identifier.getString("identifierTypeId"));
  }

  @Test
  void testStoredRequest() {
    Request request = createMockRequest();

    JsonObject storedRepresentation = new StoredRequestRepresentation()
      .storedRequest(request);

    assertThat("Stored representation should not have a delivery address",
      storedRepresentation.containsKey("deliveryAddress"), is(false));

    JsonObject identifier = storedRepresentation.getJsonObject("instance")
      .getJsonArray(IDENTIFIERS).getJsonObject(0);
    assertThat("Stored representation should contain valid identifier value",
      identifier.getString("value"), is(IDENTIFIER_VALUE));
    assertThat("Stored representation should contain valid identifier identifierTypeId",
      identifier.getString("identifierTypeId"), is(IDENTIFIER_ID.toString()));

    JsonObject searchIndex = storedRepresentation.getJsonObject("searchIndex");
    assertNotNull(searchIndex);
    assertThat("Stored representation should contain pickup service point name",
      searchIndex.getString("pickupServicePointName"), is(SERVICE_POINT_NAME_VALUE));
    assertThat("Stored representation should contain shelving order value",
      searchIndex.getString("shelvingOrder"), is(SHELVING_ORDER_VALUE));

    JsonObject callNumberComponents = searchIndex.getJsonObject("callNumberComponents");
    assertNotNull(callNumberComponents);
    assertThat("Stored representation should contain call number",
      callNumberComponents.getString("callNumber"), is(CALL_NUMBER_VALUE));
    assertThat("Stored representation should contain call number prefix",
      callNumberComponents.getString("prefix"), is(CALL_NUMBER_PREFIX_VALUE));
    assertThat("Stored representation should contain call number suffix",
      callNumberComponents.getString("suffix"), is(CALL_NUMBER_SUFFIX_VALUE));
  }

  @Test
  void testExtendedRepresentationForAnonymization() {
    RequestRepresentation requestRepresentation = new RequestRepresentation();
    Request request = createMockRequest();
    JsonObject extendedRepresentation = requestRepresentation.extendedRepresentation(request);
    JsonObject json = request.asJson().put("requesterId", (String) null);

    request = Request.from(json)
      .withInstance(request.getInstance())
      .withPickupServicePoint(request.getPickupServicePoint())
      .withItem(request.getItem());

    JsonObject out = requestRepresentation.extendedRepresentation(request);

    assertThat("Anonymized: requester must be omitted",
      out.containsKey("requester"), is(false));

    assertThat("Anonymized: deliveryAddress must be omitted",
      out.containsKey("deliveryAddress"), is(false));

    JsonObject instance = out.getJsonObject(INSTANCE);

    assertTrue(instance.containsKey(EDITIONS));
    assertTrue(instance.containsKey(PUBLICATION));
    assertTrue(instance.containsKey(IDENTIFIERS));
    assertEquals("First American Edition", instance.getJsonArray(EDITIONS).getString(0));

    JsonObject publication = instance.getJsonArray(PUBLICATION).getJsonObject(0);
    assertEquals("fake publisher", publication.getString("publisher"));
    assertEquals("fake place", publication.getString("place"));
    assertEquals("2016", publication.getString("dateOfPublication"));

    JsonObject identifier = instance.getJsonArray(IDENTIFIERS).getJsonObject(0);
    assertEquals(IDENTIFIER_VALUE, identifier.getString("value"));
    assertEquals(IDENTIFIER_ID.toString(), identifier.getString("identifierTypeId"));
  }

  @Test
  void nonAnonymizedWithRequesterExpandedIncludesRequesterAndDeliveryAddress() {
    var rep = new RequestRepresentation();
    var request = createMockRequest();

    JsonObject json = request.asJson();
    assertThat("requesterId should be present for non-anonymized",
      json.containsKey("requesterId"), is(true));

    JsonObject out = rep.extendedRepresentation(request);

    assertThat("Non-anonymized: requester should be present",
      out.containsKey("requester"), is(true));

    assertThat("Non-anonymized: deliveryAddress should be present",
      out.containsKey("deliveryAddress"), is(true));
  }

  @Test
  void proxyPresentAddsProxySummary() {
    var rep = new RequestRepresentation();
    var request = createMockRequest();

    var proxy = new User(new api.support.builders.UserBuilder()
      .withName("Proxy", "Person").withBarcode("P-001").create());
    request = request.withProxy(proxy);

    JsonObject out = rep.extendedRepresentation(request);

    assertThat("Proxy summary should be present when proxy exists",
      out.containsKey("proxy"), is(true));
    assertThat("Proxy lastName", out.getJsonObject("proxy").getString("lastName"), is("Person"));
  }

  @Test
  void proxyMissingRemovesStaleProxyKeyIfPresentInBaseJson() {
    var rep = new RequestRepresentation();
    var request = createMockRequest();

    JsonObject base = request.asJson().put("proxy", new JsonObject().put("firstName", "Stale"));
    request = Request.from(base)
      .withInstance(request.getInstance())
      .withPickupServicePoint(request.getPickupServicePoint())
      .withItem(request.getItem());

    JsonObject out = rep.extendedRepresentation(request);

    assertThat("Stale proxy key should be removed when no proxy is present",
      out.containsKey("proxy"), is(false));
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
      = new ServicePoint(SERVICE_POINT_ID.toString(), SERVICE_POINT_NAME_VALUE, "cd1", true,
      "Circulation Desk", null, null, null, null);

    Instance instance = new Instance(UUID.randomUUID().toString(), "1234", null,
      List.of(new Identifier(IDENTIFIER_ID.toString(), IDENTIFIER_VALUE)),
      emptyList(),
      List.of(new Publication("fake publisher", "fake place", "2016", null)),
      List.of("First American Edition"),
      List.of("Hardback"),
      emptyList());

    JsonObject itemJson = new JsonObject()
      .put("effectiveCallNumberComponents", new JsonObject()
        .put("callNumber", CALL_NUMBER_VALUE)
        .put("prefix", CALL_NUMBER_PREFIX_VALUE)
        .put("suffix", CALL_NUMBER_SUFFIX_VALUE))
      .put("effectiveShelvingOrder", SHELVING_ORDER_VALUE);
    final Item item = Item.from(itemJson)
      .withInstance(instance);

    JsonObject requestJsonObject = new RequestBuilder()
      .recall()
      .withId(REQUEST_ID)
      .withInstanceId(INSTANCE_ID)
      .withRequestDate(requestDate)
      .itemRequestLevel()
      .withItemId(ITEM_ID)
      .withRequesterId(requesterId)
      .fulfillToHoldShelf()
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
