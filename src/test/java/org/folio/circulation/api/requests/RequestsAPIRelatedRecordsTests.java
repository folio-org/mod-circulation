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
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

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
}
