package api.requests;

import static api.APITestSuite.thirdFloorLocationId;
import static api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Ignore;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.HoldingBuilder;
import api.support.builders.RequestBuilder;
import api.support.builders.UserBuilder;
import api.support.fixtures.InstanceExamples;
import api.support.fixtures.ItemExamples;
import io.vertx.core.json.JsonObject;

public class RequestsAPIRelatedRecordsTests extends APITests {

  @Test
  public void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
      .withItemId(itemId));

    JsonObject createdRequest = response.getJson();

    assertThat("has holdings record ID",
      createdRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      createdRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));

    assertThat("has instance ID",
      createdRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      createdRequest.getJsonObject("item").getString("instanceId"),
      is(instanceId.toString()));

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has holdings record ID",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));

    assertThat("has instance ID",
      fetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequest.getJsonObject("item").getString("instanceId"),
      is(instanceId.toString()));
  }

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void holdingIdAndInstanceIdIncludedWhenInstanceNotAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
      .withItemId(itemId));

    instancesClient.delete(instanceId);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has holdings record ID",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));

    assertThat("has instance ID",
      fetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequest.getJsonObject("item").getString("instanceId"),
      is(instanceId.toString()));
  }

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noInstanceIdIncludedWhenHoldingNotAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
      .withItemId(itemId));

    holdingsClient.delete(holdingId);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has holdings record ID",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(holdingId.toString()));

    assertThat("has no instance ID",
      fetchedRequest.getJsonObject("item").containsKey("instanceId"), is(false));
  }

  @Test
  public void holdingAndInstanceIdComesFromMultipleRecordsForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    loansFixture.checkOutItem(firstItemId);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    loansFixture.checkOutItem(secondItemId);

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(secondItemId)).getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("has holdings record ID",
      firstFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstFetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(firstHoldingId.toString()));

    assertThat("has instance ID",
      firstFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      firstFetchedRequest.getJsonObject("item").getString("instanceId"),
      is(firstInstanceId.toString()));

    assertThat("has holdings record ID",
      secondFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondFetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(secondHoldingId.toString()));

    assertThat("has instance ID",
      secondFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      secondFetchedRequest.getJsonObject("item").getString("instanceId"),
      is(secondInstanceId.toString()));
  }

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noInstanceIdForMultipleRequestsWhenHoldingNotFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    loansFixture.checkOutItem(firstItemId);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    loansFixture.checkOutItem(secondItemId);

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(secondItemId)).getId();

    //Delete a holding
    holdingsClient.delete(secondHoldingId);

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("first request has instance ID",
      firstFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("second request does not have instance ID",
      secondFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(false));
  }

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void instanceIdAndHoldingIdForMultipleRequestsWhenInstanceNotFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(firstInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    loansFixture.checkOutItem(firstItemId);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .withPermanentLocation(thirdFloorLocationId())
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    loansFixture.checkOutItem(secondItemId);

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(secondItemId)).getId();

    instancesClient.delete(firstInstanceId);
    instancesClient.delete(secondInstanceId);

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("first request has holdings record ID",
      firstFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("first request has instance ID",
      firstFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("second request has holdings record ID",
      secondFetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("second request has instance ID",
      secondFetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));
  }
}
