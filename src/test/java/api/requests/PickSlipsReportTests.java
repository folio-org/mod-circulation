package api.requests;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Assert;
import org.junit.Test;

import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.Response;

public class PickSlipsReportTests extends APITests {

  private static final int UUID_BATCH_SIZE = 40;

  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String ITEMS_KEY = "items";
  private static final String ID_KEY = "id";
  private static final String TITLE_KEY = "title";
  private static final String CONTRIBUTORS_KEY = "contributors";
  private static final String LOCATION_KEY = "location";
  private static final String NAME_KEY = "name";
  private static final String CALL_NUMBER_KEY = "callNumber";

  @Test
  public void reportIsEmptyForNonExistentServicePointId()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlipsReport(client).getById(UUID.randomUUID());
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyForWrongServicePointId()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    UUID differentServicePointId = servicePointsFixture.cd2().getId();
    Response response = ResourceClient.forPickSlipsReport(client).getById(differentServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyWhenThereAreNoPagedItems()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportIsEmptyWhenPagedItemHasOpenRequestWithWrongStatus()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .page()
        .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 0);
  }

  @Test
  public void reportContainsPagedItemWithOpenUnfilledRequest()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    UUID servicePointId = servicePointsFixture.cd1().getId();
    InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 1);
    assertResponseContainsAllRequiredFields(response);
  }

  @Test
  public void reportContainsPagedItemWithMultipleOpenUnfilledRequests()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

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

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 1);
    assertResponseContainsAllRequiredFields(response);
  }

  @Test
  public void multiBatchRequestFetchingProcessesAllRequests()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    UUID servicePointId = servicePointsFixture.cd1().getId();

    final int itemsCount = UUID_BATCH_SIZE + 1;
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

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, itemsCount);

    Set<String> itemIdsFromResponse = response
        .getJson()
        .getJsonArray(ITEMS_KEY)
        .stream()
        .map(item -> ((JsonObject) item).getString(ID_KEY))
        .collect(Collectors.toSet());

    Assert.assertEquals(expectedItemIds, itemIdsFromResponse);
  }

  @Test
  public void reportIncludesItemsFromDifferentLocationsForSameServicePoint()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Second floor
    IndividualResource secondFloorCd1 = locationsFixture.secondFloorEconomics();
    final IndividualResource temeraireSecondFloorCd1 = itemsFixture.basedUponTemeraire(
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
    final IndividualResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
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

    Response response = ResourceClient.forPickSlipsReport(client).getById(circDesk1);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(response, 2);
    assertResponseContainsAllRequiredFields(response);
  }

  @Test
  public void reportDoesNotIncludeItemsFromDifferentServicePoint()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID circDesk1 = servicePointsFixture.cd1().getId();

    // Circ desk 1: Third floor
    IndividualResource thirdFloorCd1 = locationsFixture.thirdFloor();
    final IndividualResource planetThirdFloorCd1 = itemsFixture.basedUponSmallAngryPlanet(
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
    final IndividualResource planetSecondFloorCd4 = itemsFixture.basedUponSmallAngryPlanet(
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
    Response responseForCd1 = ResourceClient.forPickSlipsReport(client).getById(circDesk1);
    assertThat(responseForCd1.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(responseForCd1, 1);
    assertResponseContainsAllRequiredFields(responseForCd1);
    assertThat(responseForCd1.getJson().getJsonArray(ITEMS_KEY).getJsonObject(0).getString(ID_KEY),
        is(planetThirdFloorCd1.getId().toString()));

    // Report for Circ Desk 4
    UUID circDesk4 = servicePointsFixture.cd4().getId();
    Response responseForCd4 = ResourceClient.forPickSlipsReport(client).getById(circDesk4);
    assertThat(responseForCd4.getStatusCode(), is(HTTP_OK));

    assertResponseHasItems(responseForCd4, 1);
    assertResponseContainsAllRequiredFields(responseForCd4);
    assertThat(responseForCd4.getJson().getJsonArray(ITEMS_KEY).getJsonObject(0).getString(ID_KEY),
        is(planetSecondFloorCd4.getId().toString()));
  }

  private void assertResponseContainsAllRequiredFields(Response response) {
    response
        .getJson()
        .getJsonArray(ITEMS_KEY).stream()
        .map(JsonObject.class::cast)
        .forEach(item -> {
          assertTrue(StringUtils.isNoneBlank(
              item.getString(ID_KEY),
              item.getString(TITLE_KEY),
              item.getString(CALL_NUMBER_KEY),
              item.getJsonObject(LOCATION_KEY).getString(NAME_KEY)
          ));
          assertFalse(item.getJsonArray(CONTRIBUTORS_KEY).isEmpty());
        });
  }

  private void assertResponseHasItems(Response response, int itemsCount) {
    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));
  }

}
