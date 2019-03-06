package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.UUIDMatcher.is;
import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIRelatedRecordsTests extends APITests {
  private static final String ONE_COPY_NUMBER = "1";
  private static final String TWO_COPY_NUMBER = "2";

  @Test
  public void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte()));

    JsonObject createdRequest = response.getJson();

    assertThat("has holdings record ID",
      createdRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      createdRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      createdRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      createdRequest.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    Response fetchedRequestResponse = requestsClient.getById(response.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has holdings record ID",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequest.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));
  }

  @Test
  public void checkRelatedRecordsForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder.withCopyNumbers(asList(ONE_COPY_NUMBER, TWO_COPY_NUMBER))
    );

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(smallAngryPlanet);
    loansFixture.checkOutByBarcode(temeraire);

    final IndividualResource charlotte = usersFixture.charlotte();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .by(charlotte))
      .getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .forItem(temeraire)
      .by(charlotte))
      .getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstItem = getRecordById(fetchedRequestsResponse, firstRequestId).
      get()
      .getJsonObject("item");

    assertThat("has holdings record ID",
      firstItem.containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstItem.getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      firstItem.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      firstItem.getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    assertThat(firstItem.containsKey("copyNumbers"), is(true));
    assertThat(firstItem.getJsonArray("copyNumbers"), contains(ONE_COPY_NUMBER, TWO_COPY_NUMBER));

    JsonObject secondItem = getRecordById(fetchedRequestsResponse, secondRequestId)
      .get()
      .getJsonObject("item");

    assertThat("has holdings record ID",
      secondItem.containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondItem.getString("holdingsRecordId"),
      is(temeraire.getHoldingsRecordId()));

    assertThat("has instance ID",
      secondItem.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      secondItem.getString("instanceId"),
      is(temeraire.getInstanceId()));
  }
}
