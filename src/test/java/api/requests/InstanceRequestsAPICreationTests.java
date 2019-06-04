package api.requests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.APITests;
import api.support.http.InterfaceUrls;
import io.vertx.core.json.JsonObject;

public class InstanceRequestsAPICreationTests extends APITests {

  @Test
  public void canCreateATitleLevelRequestForMultipleAvailableItemsAndAMatchingPickupLocationId()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instanceMultipleCopies = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instanceMultipleCopies.getId());

    IndividualResource locationsResource = locationsFixture.mainFloor();

    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);
    final IndividualResource item2 = itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), locationsResource.getId());
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);

    JsonObject requestBody = createInstanceRequestObject(instanceMultipleCopies.getId(), requesterId,
                                            pickupServicePointId, requestDate, requestExpirationDate);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl("/instances"), requestBody,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(10, TimeUnit.SECONDS);

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instanceMultipleCopies.getId(),
      item2.getId(),
      RequestType.PAGE);
  }

  @Test
  public void canCreateATitleLevelRequestForOneAvailableCopy()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instance.getId());

    final IndividualResource item = itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);

    JsonObject requestBody = createInstanceRequestObject(instance.getId(), requesterId,
      pickupServicePointId, requestDate, requestExpirationDate);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl("/instances"), requestBody,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(10, TimeUnit.SECONDS);

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instance.getId(),
      item.getId(),
      RequestType.PAGE);

  }

  @Test
  public void canCreateATitleLevelRequestForMultipleAvailableItemsWithNoMatchingPickupLocationIds()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    UUID requesterId = usersFixture.jessica().getId();
    DateTime requestDate = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
    DateTime requestExpirationDate = requestDate.plusDays(30);

    IndividualResource instance = instancesFixture.basedUponDunkirk();
    IndividualResource holdings = holdingsFixture.defaultWithHoldings(instance.getId());

    //create 3 copies with no location id's assigned.
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);
    itemsFixture.basedUponDunkirkWithCustomHoldingAndLocation(holdings.getId(), null);

    JsonObject requestBody = createInstanceRequestObject(instance.getId(), requesterId,
      pickupServicePointId, requestDate, requestExpirationDate);

    CompletableFuture<Response> postCompleted = new CompletableFuture<>();

    client.post(InterfaceUrls.requestsUrl("/instances"), requestBody,
      ResponseHandler.any(postCompleted));

    Response postResponse = postCompleted.get(100, TimeUnit.SECONDS);

    JsonObject representation = postResponse.getJson();
    validateInstanceRequestResponse(representation,
      pickupServicePointId,
      instance.getId(),
      null,  //although we create the items in certain order, this order may not be what the code picks up when query for items by holdings, so there is no point to check for itemId
      RequestType.PAGE);

  }

    private void validateInstanceRequestResponse(JsonObject representation,
                                                 UUID pickupServicePointId,
                                                 UUID instanceId,
                                                 UUID itemId,
                                                 RequestType expectedRequestType){
      assertNotNull(representation);
      assertEquals(pickupServicePointId.toString(), representation.getString("pickupServicePointId"));
      assertEquals("Circ Desk 1", representation.getJsonObject("pickupServicePoint").getString("name"));
      assertEquals(instanceId.toString(), representation.getJsonObject("item").getString("instanceId"));
      assertEquals(expectedRequestType.name(), representation.getString("requestType"));
      if (itemId != null)
        assertEquals(itemId.toString(), representation.getString("itemId"));
    }

    private JsonObject createInstanceRequestObject(UUID instanceId, UUID requesterId, UUID pickupServicePointId,
                                                   DateTime requestDate, DateTime requestExpirationDate){
      JsonObject requestBody = new JsonObject();
      requestBody.put("instanceId", instanceId.toString());
      requestBody.put("requestDate", requestDate.toString(ISODateTimeFormat.dateTime()));
      requestBody.put("requesterId", requesterId.toString());
      requestBody.put("pickupServicePointId", pickupServicePointId.toString());
      requestBody.put("fulfilmentPreference", "Hold Shelf");
      requestBody.put("requestExpirationDate", requestExpirationDate.toString(ISODateTimeFormat.dateTime()));

      return requestBody;
    }
}
