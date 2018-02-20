package org.folio.circulation.api.requests;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.api.support.APITests;
import org.folio.circulation.api.support.builders.HoldingBuilder;
import org.folio.circulation.api.support.builders.RequestBuilder;
import org.folio.circulation.api.support.builders.UserBuilder;
import org.folio.circulation.api.support.fixtures.InstanceExamples;
import org.folio.circulation.api.support.fixtures.ItemExamples;
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
      InstanceExamples.basedUponSmallAngryPlanet()).getId();

    UUID holdingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(instanceId)
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

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
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

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
        .create())
      .getId();

    UUID itemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(holdingId))
      .getId();

    checkOutItem(itemId, loansClient);

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
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

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
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

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
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID secondInstanceId = instancesClient.create(
      InstanceExamples.basedUponTemeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(secondItemId, loansClient);

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
