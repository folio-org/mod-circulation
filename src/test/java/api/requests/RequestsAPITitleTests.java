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

public class RequestsAPITitleTests extends APITests {

  @Test
  public void titleIsFromInstanceWhenCreatingRequestWithHoldingAndInstance()
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

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noTitleWhenCreatingRequestForNotFoundHolding()
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

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noTitleWhenCreatingRequestAndInstanceNotFound()
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

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noTitleWhenRequestUpdatedAndInstanceNotFound()
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

    instancesClient.delete(instanceId);

    requestsClient.replace(requestId, createdRequest);

    Response fetchedRequestResponse = requestsClient.getById(requestId);

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has no title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(false));
  }

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
  @Test
  public void noTitleWhenRequestUpdatedAndHoldingNotFound()
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
        .withPermanentLocation(thirdFloorLocationId())
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
        .withPermanentLocation(thirdFloorLocationId())
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

  @Ignore("mod-inventory-storage disallows this scenario, change to be isolated test")
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
        .withPermanentLocation(thirdFloorLocationId())
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
        .withPermanentLocation(thirdFloorLocationId())
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
