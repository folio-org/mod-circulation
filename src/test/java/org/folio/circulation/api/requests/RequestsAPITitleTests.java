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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.JsonCollectionAssistant.getRecordById;
import static org.folio.circulation.api.support.fixtures.LoansFixture.checkOutItem;
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

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
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

    loansFixture.checkOutItem(itemId);

    holdingsClient.delete(holdingId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
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

    loansFixture.checkOutItem(itemId);

    instancesClient.delete(instanceId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
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

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
      .withItemId(itemId));

    JsonObject createdRequest = response.getJson();

    instancesClient.replace(instanceId,
      InstanceExamples.basedUponSmallAngryPlanet()
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

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
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

    loansFixture.checkOutItem(itemId);

    UUID requestId = UUID.randomUUID();

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .withId(requestId)
      .withRequesterId(usersClient.create(new UserBuilder().create()).getId())
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

    loansFixture.checkOutItem(firstItemId);

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    loansFixture.checkOutItem(secondItemId);

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
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

    loansFixture.checkOutItem(firstItemId);

    UUID requesterId = usersClient.create(new UserBuilder().create()).getId();

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withRequesterId(requesterId)
      .withItemId(firstItemId)).getId();

    loansFixture.checkOutItem(secondItemId);

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
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
