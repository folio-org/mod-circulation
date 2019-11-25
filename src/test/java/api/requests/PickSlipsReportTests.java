package api.requests;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
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
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
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
  private static final String LOCATION_KEY = "location";
  private static final String NAME_KEY = "name";
  private static final String CALL_NUMBER_KEY = "callNumber";

  @Test
  public void reportContainsPagedItemWithOpenUnfilledRequest()
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

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    JsonArray itemsJson = responseJson.getJsonArray(ITEMS_KEY);
    assertThat(itemsJson.size(), is(1));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(1));

    assertTrue(reportContainsAllRequiredFields(itemsJson.getJsonObject(0)));
  }

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

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(0));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
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

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointsFixture.cd2().getId());
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(0));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
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

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(0));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void reportIsEmptyWhenPagedItemHasRequestWithWrongStatus()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_AWAITING_PICKUP.getValue())
        .page()
        .withPickupServicePointId(servicePointId)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlipsReport(client).getById(servicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(0));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void reportIsEmptyDueToLocationMismatch()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID wrongServicePoint = servicePointsFixture.cd2().getId();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    requestsFixture.place(new RequestBuilder()
        .withStatus(RequestStatus.OPEN_NOT_YET_FILLED.getValue())
        .page()
        .withPickupServicePointId(wrongServicePoint)
        .forItem(smallAngryPlanet)
        .by(usersFixture.james()));

    Response response = ResourceClient.forPickSlipsReport(client).getById(wrongServicePoint);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getJsonArray(ITEMS_KEY).size(), is(0));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void multiBatchRequestFetchingWorksCorrectly()
      throws InterruptedException,
      MalformedURLException,
      TimeoutException,
      ExecutionException {

    final UUID servicePointId = servicePointsFixture.cd1().getId();

    int itemsCount = UUID_BATCH_SIZE + 1;
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

    JsonObject responseJson = response.getJson();
    JsonArray itemsJson = responseJson.getJsonArray(ITEMS_KEY);

    assertThat(itemsJson.size(), is(itemsCount));
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(itemsCount));

    Set<String> itemIdsFromResponse = itemsJson.stream()
        .map(item -> ((JsonObject) item).getString(ID_KEY))
        .collect(Collectors.toSet());

    Assert.assertEquals(expectedItemIds, itemIdsFromResponse);
  }

  private boolean reportContainsAllRequiredFields(JsonObject item) {
    return StringUtils.isNoneBlank(
        item.getString(ID_KEY),
        item.getString(TITLE_KEY),
        item.getString(CALL_NUMBER_KEY),
        item.getJsonObject(LOCATION_KEY).getString(NAME_KEY));
  }

}
