package api.requests;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Assert;
import org.junit.Test;

import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.Response;

public class PickSlipsTests extends APITests {
  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String PICK_SLIPS_KEY = "pickSlips";
  private static final String ID_KEY = "id";
  private static final String HOLDINGS_RECORD_ID_KEY = "holdingsRecordId";
  private static final String INSTANCE_ID_KEY = "instanceId";
  private static final String TITLE_KEY = "title";
  private static final String BARCODE_KEY = "barcode";
  private static final String CALL_NUMBER_KEY = "callNumber";
  private static final String STATUS_KEY = "status";
  private static final String CONTRIBUTORS_KEY = "contributors";
  private static final String LOCATION_KEY = "location";
  private static final String NAME_KEY = "name";
  private static final String CODE_KEY = "code";

  @Test
  public void reportIsEmptyForNonExistentServicePointId() {

    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlips().getById(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyForWrongServicePointId() {

    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    UUID differentServicePointId = servicePointsFixture.cd2().getId();
    Response response = ResourceClient.forPickSlips().getById(differentServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyWhenThereAreNoPagedItems() {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    Response response = ResourceClient.forPickSlips().getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyWhenPagedItemHasOpenRequestWithWrongStatus() {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .page()
        .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportContainsPagedItemWithOpenUnfilledRequest() {

    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 1);
    validateResponse(response, smallAngryPlanet);
  }

  @Test
  public void reportContainsPagedItemWithMultipleOpenUnfilledRequests() {

    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    RequestBuilder firstRequestBuilder = new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james());

    requestsClient.create(firstRequestBuilder);

    RequestBuilder secondRequestBuilder = new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .hold()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.steve());

    IndividualResource secondRequest = requestsClient.create(secondRequestBuilder);
    requestsClient.replace(secondRequest.getId(),
        secondRequestBuilder.withRequestType(RequestType.PAGE.getValue()));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 1);
    validateResponse(response, smallAngryPlanet);
  }

  @Test
  public void multiBatchFetchingProcessesAllItemsAndRequests() {

    UUID servicePointId = servicePointsFixture.cd1().getId();

    // MultipleRecordFetcher has a limit of 50 UUIDs for a single GET request
    final int itemsCount = 51;
    Set<String> expectedItemIds = new HashSet<>(itemsCount);

    for (int i = 0; i < itemsCount; i++) {
      final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
      expectedItemIds.add(smallAngryPlanet.getId().toString());

      requestsFixture.place(new RequestBuilder()
          .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
          .page()
          .withPickupServicePointId(servicePointId)
          .forItem(smallAngryPlanet)
          .by(usersFixture.james()));
    }

    assertThat(expectedItemIds.size(), is(itemsCount));

    Response response = ResourceClient.forPickSlips().getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, itemsCount);

    Set<String> itemIdsFromResponse = response
        .getJson()
        .getJsonArray(PICK_SLIPS_KEY)
        .stream()
        .map(item -> ((JsonObject) item).getString(ID_KEY))
        .collect(Collectors.toSet());

    Assert.assertEquals(expectedItemIds, itemIdsFromResponse);
  }

  @Test
  public void reportIncludesItemsFromDifferentLocationsForSameServicePoint() {

    final UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Second floor
    IndividualResource secondFloorCd1 = locationsFixture.secondFloorEconomics();
    final InventoryItemResource temeraireSecondFloorCd1 = itemsFixture.basedUponTemeraire(
        holdingBuilder -> holdingBuilder
            .withPermanentLocation(secondFloorCd1)
            .withNoTemporaryLocation(),
        itemBuilder -> itemBuilder
            .withNoPermanentLocation()
            .withNoTemporaryLocation());

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(circDesk1)
        .forItem(temeraireSecondFloorCd1)
        .by(usersFixture.james()));

    // Circ desk 1: Third floor
    IndividualResource thirdFloorCd1 = locationsFixture.thirdFloor();
    final InventoryItemResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
        holdingBuilder -> holdingBuilder
            .withPermanentLocation(thirdFloorCd1)
            .withNoTemporaryLocation(),
        itemBuilder -> itemBuilder
            .withNoPermanentLocation()
            .withNoTemporaryLocation());

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(circDesk1)
        .forItem(planetThirdFloorCd1)
        .by(usersFixture.charlotte()));

    Response response = ResourceClient.forPickSlips().getById(circDesk1);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 2);
    validateResponse(response, temeraireSecondFloorCd1, planetThirdFloorCd1);
  }

  @Test
  public void reportDoesNotIncludeItemsFromDifferentServicePoint() {

    final UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Third floor
    IndividualResource thirdFloorCd1 = locationsFixture.thirdFloor();
    final InventoryItemResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
        holdingBuilder -> holdingBuilder
            .withPermanentLocation(thirdFloorCd1)
            .withNoTemporaryLocation(),
        itemBuilder -> itemBuilder
            .withNoPermanentLocation()
            .withNoTemporaryLocation());

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(circDesk1)
        .forItem(planetThirdFloorCd1)
        .by(usersFixture.charlotte()));

    // Circ desk 4: Second floor
    IndividualResource secondFloorCd4 = locationsFixture.fourthServicePoint();
    final InventoryItemResource planetSecondFloorCd4 = itemsFixture.basedUponSmallAngryPlanet(
        holdingBuilder -> holdingBuilder
            .withPermanentLocation(secondFloorCd4)
            .withNoTemporaryLocation(),
        itemBuilder -> itemBuilder
            .withNoPermanentLocation()
            .withNoTemporaryLocation());

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(circDesk1)
        .forItem(planetSecondFloorCd4)
        .by(usersFixture.jessica()));

    // Report for Circ Desk 1
    Response responseForCd1 = ResourceClient.forPickSlips().getById(circDesk1);
    assertThat(responseForCd1.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(responseForCd1, 1);
    validateResponse(responseForCd1, planetThirdFloorCd1);
    assertThat(responseForCd1.getJson().getJsonArray(PICK_SLIPS_KEY).getJsonObject(0).getString(ID_KEY),
        is(planetThirdFloorCd1.getId().toString()));

    // Report for Circ Desk 4
    UUID circDesk4 = servicePointsFixture.cd4().getId();
    Response responseForCd4 = ResourceClient.forPickSlips().getById(circDesk4);
    assertThat(responseForCd4.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(responseForCd4, 1);
    validateResponse(responseForCd4, planetSecondFloorCd4);
    assertThat(responseForCd4.getJson().getJsonArray(PICK_SLIPS_KEY).getJsonObject(0).getString(ID_KEY),
        is(planetSecondFloorCd4.getId().toString()));
  }

  private void validateResponse(Response response, InventoryItemResource... sourceItems) {
    Stream.of(sourceItems).forEach(sourceItem -> {
      Optional<JsonObject> matchingItemFromResponse = response
          .getJson()
          .getJsonArray(PICK_SLIPS_KEY).stream()
          .map(JsonObject.class::cast)
          .filter(itemFromResponse -> sourceItem.getId().toString().equals(itemFromResponse.getString(ID_KEY)))
          .findFirst();

      if (matchingItemFromResponse.isPresent()) {
        validateItem(matchingItemFromResponse.get(), sourceItem);
      } else {
        fail("Expected item is missing in response: itemId=" + sourceItem.getId());
      }
    });
  }

  private void validateItem(JsonObject item, InventoryItemResource sourceItem) {
    assertThat(item.getString(ID_KEY), is(sourceItem.getId().toString()));
    assertThat(item.getString(INSTANCE_ID_KEY), is(sourceItem.getInstanceId().toString()));
    assertThat(item.getString(HOLDINGS_RECORD_ID_KEY), is(sourceItem.getHoldingsRecordId().toString()));
    assertThat(item.getString(BARCODE_KEY), is(sourceItem.getBarcode()));
    assertThat(item.getString(TITLE_KEY), is(sourceItem.getInstance().getResponse().getJson().getString(TITLE_KEY)));

    JsonArray contributorsFromSource = sourceItem.getInstance().getJson().getJsonArray(CONTRIBUTORS_KEY);
    if (contributorsFromSource != null) {
      assertEquals(contributorsFromSource.size(), item.getJsonArray(CONTRIBUTORS_KEY).size());
    }

    JsonObject location = item.getJsonObject(LOCATION_KEY);
    assertTrue(StringUtils.isNoneBlank(
        item.getString(CALL_NUMBER_KEY),
        item.getString(STATUS_KEY),
        location.getString(NAME_KEY),
        location.getString(CODE_KEY)
    ));
  }

  private void assertResponseHasItems(Response response, int itemsCount) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(PICK_SLIPS_KEY).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));
  }

}
