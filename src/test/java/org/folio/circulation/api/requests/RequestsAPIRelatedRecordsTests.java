package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.HoldingRequestBuilder;
import org.folio.circulation.api.support.builders.RequestRequestBuilder;
import org.folio.circulation.api.support.builders.UserRequestBuilder;
import org.folio.circulation.api.support.fixtures.InstanceRequestExamples;
import org.folio.circulation.api.support.fixtures.ItemRequestExamples;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.api.support.fixtures.LoanFixture.checkOutItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPIRelatedRecordsTests extends APITests {

  @Test
  public void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestRequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserRequestBuilder().create()).getId())
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

  @Test
  public void holdingIdAndInstanceIdIncludedWhenInstanceNotAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

    UUID requestId = UUID.randomUUID();

    requestsClient.create(new RequestRequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserRequestBuilder().create()).getId())
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

  @Test
  public void noInstanceIdIncludedWhenHoldingNotAvailable()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

    UUID requestId = UUID.randomUUID();

    requestsClient.create(new RequestRequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserRequestBuilder().create()).getId())
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
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestRequestBuilder()
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

  @Test
  public void noInstanceIdForMultipleRequestsWhenHoldingNotFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestRequestBuilder()
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

  @Test
  public void instanceIdAndHoldingIdForMultipleRequestsWhenInstanceNotFound()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponSmallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    UUID secondRequestId = requestsClient.create(new RequestRequestBuilder()
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
