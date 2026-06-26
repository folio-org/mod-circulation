package api.requests;

import static api.support.http.InterfaceUrls.circulationRequestAnonymizationUrl;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class RequestAnonymizationApiTests extends APITests {

  @Test
  void anonymizeSingleRequestRemovesPIIAndReturns200() {
    String requestId = createClosedDeliveryRequest();

    Response response = restAssuredClient.post(
      circulationRequestAnonymizationUrl(requestId),
      "anonymize-request-" + requestId
    );

    assertEquals(200, response.getStatusCode());

    Response updated =
      requestsStorageClient.getById(UUID.fromString(requestId));
    JsonObject stored = updated.getJson();

    assertTrue(stored.containsKey("requesterId"));
    assertNull(stored.getValue("requesterId"));
    assertTrue(stored.containsKey("proxyUserId"));
    assertNull(stored.getValue("proxyUserId"));

    assertFalse(stored.containsKey("requester"));
    assertFalse(stored.containsKey("proxy"));
    assertFalse(stored.containsKey("deliveryAddressTypeId"));
  }

  private String createClosedDeliveryRequest() {
    var user = usersFixture.james();
    var item = itemsFixture.basedUponSmallAngryPlanet();

    IndividualResource request = requestsStorageClient.create(
      new RequestBuilder()
        .forItem(item)
        .by(user)
        .fulfilled()
        .withFulfillmentPreference("Delivery")
        .withDeliveryAddressType(UUID.randomUUID())
        .create()
    );

    return request.getId().toString();
  }
}
