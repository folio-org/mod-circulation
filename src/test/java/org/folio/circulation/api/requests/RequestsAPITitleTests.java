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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.api.support.fixtures.LoanFixture.checkOutItem;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPITitleTests extends APITests {

  @Test
  public void titleIsFromInstanceWhenCreatingRequestWithHoldingAndInstance()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    assertThat("has item title",
      createdRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      createdRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has item title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      fetchedRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));
  }

  @Test
  public void noTitleWhenCreatingRequestForNotFoundHolding()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    holdingsClient.delete(holdingId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestRequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserRequestBuilder().create()).getId())
      .withItemId(itemId));

    JsonObject createdRequest = response.getJson();

    assertThat("has no title",
      createdRequest.getJsonObject("item").containsKey("title"), is(false));

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has no title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(false));
  }

  @Test
  public void noTitleWhenCreatingRequestAndInstanceNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    instancesClient.delete(instanceId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestRequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserRequestBuilder().create()).getId())
      .withItemId(itemId));

    JsonObject createdRequest = response.getJson();

    assertThat("has no title",
      createdRequest.getJsonObject("item").containsKey("title"), is(false));

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has no title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(false));
  }

  @Test
  public void titleIsChangedWhenRequestUpdatedAndInstanceTitleChanged()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    instancesClient.replace(instanceId,
      InstanceRequestExamples.smallAngryPlanet()
        .withId(instanceId)
        .withTitle("A new instance title"));

    requestsClient.replace(requestId, createdRequest);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has item title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      fetchedRequest.getJsonObject("item").getString("title"),
      is("A new instance title"));
  }

  @Test
  public void noTitleWhenRequestUpdatedAndInstanceNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    instancesClient.delete(instanceId);

    requestsClient.replace(requestId, createdRequest);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has no title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(false));
  }

  @Test
  public void noTitleWhenRequestUpdatedAndHoldingNotFound()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException,
    UnsupportedEncodingException {

    UUID instanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

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

    holdingsClient.delete(holdingId);

    requestsClient.replace(requestId, createdRequest);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has no title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(false));
  }

  @Test
  public void titlesComeFromMultipleInstancesForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.temeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    checkOutItem(secondItemId, loansClient);

    UUID secondRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(secondItemId)).getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("has item title",
      firstFetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      firstFetchedRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("has item title",
      secondFetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      secondFetchedRequest.getJsonObject("item").getString("title"),
      is("Temeraire"));
  }

  @Test
  public void titlesForMultipleRequestsAreUnaffectedByDeletionOfHoldingOrInstance()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID firstInstanceId = instancesClient.create(
      InstanceRequestExamples.smallAngryPlanet()).getId();

    UUID firstHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(firstInstanceId)
        .create())
      .getId();

    UUID firstItemId = itemsClient.create(
      ItemRequestExamples.basedUponSmallAngryPlanet()
        .forHolding(firstHoldingId))
      .getId();

    UUID secondInstanceId = instancesClient.create(
      InstanceRequestExamples.temeraire()).getId();

    UUID secondHoldingId = holdingsClient.create(
      new HoldingRequestBuilder()
        .forInstance(secondInstanceId)
        .create())
      .getId();

    UUID secondItemId = itemsClient.create(
      ItemRequestExamples.basedUponTemeraire()
        .forHolding(secondHoldingId))
      .getId();

    checkOutItem(firstItemId, loansClient);

    UUID requesterId = usersClient.create(new UserRequestBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    checkOutItem(secondItemId, loansClient);

    UUID secondRequestId = requestsClient.create(new RequestRequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(secondItemId)).getId();

    instancesClient.delete(firstInstanceId);
    holdingsClient.delete(secondHoldingId);

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstFetchedRequest = getRecordById(
      fetchedRequestsResponse, firstRequestId).get();

    JsonObject secondFetchedRequest = getRecordById(
      fetchedRequestsResponse, secondRequestId).get();

    assertThat("has item title",
      firstFetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      firstFetchedRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    assertThat("has item title",
      secondFetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      secondFetchedRequest.getJsonObject("item").getString("title"),
      is("Temeraire"));
  }
}
