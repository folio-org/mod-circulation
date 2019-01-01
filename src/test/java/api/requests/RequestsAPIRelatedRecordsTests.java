package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.UUIDMatcher.is;
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
import api.support.builders.UserBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIRelatedRecordsTests extends APITests {

  @Test
  public void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOutItem(smallAngryPlanet.getId());

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
      .withItemId(smallAngryPlanet.getId()));

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

    Response fetchedRequestResponse = requestsClient.getById(requestId);

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
  public void holdingAndInstanceIdComesFromMultipleRecordsForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutItem(smallAngryPlanet.getId());
    loansFixture.checkOutItem(temeraire.getId());

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(smallAngryPlanet.getId())).getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(temeraire.getId())).getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("has holdings record ID",
      firstFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstFetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      firstFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      firstFetchedRequest.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    assertThat("has holdings record ID",
      secondFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondFetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(temeraire.getHoldingsRecordId()));

    assertThat("has instance ID",
      secondFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      secondFetchedRequest.getJsonObject("item").getString("instanceId"),
      is(temeraire.getInstanceId()));
  }
}
