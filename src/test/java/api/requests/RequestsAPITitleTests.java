package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.List;
import java.util.UUID;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class RequestsAPITitleTests extends APITests {

  @Test
  void titleIsFromInstanceWhenCreatingRequestWithHoldingAndInstance() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.james()));

    JsonObject createdRequest = response.getJson();

    assertThat("has item title",
      createdRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      createdRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));

    Response fetchedRequestResponse = requestsClient.getById(response.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has item title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      fetchedRequest.getJsonObject("item").getString("title"),
      is("The Long Way to a Small, Angry Planet"));
  }

  @Test
  void titleIsChangedWhenRequestUpdatedAndInstanceTitleChanged() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.steve()));

    JsonObject createdRequest = response.getJson();

    //TODO: Replace with builder loaded from existing JSON
    instancesClient.replace(smallAngryPlanet.getInstanceId(),
      smallAngryPlanet.getInstance().copyJson()
        .put("title", "A new instance title"));

    requestsClient.replace(response.getId(), createdRequest);

    Response fetchedRequestResponse = requestsClient.getById(response.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has item title",
      fetchedRequest.getJsonObject("item").containsKey("title"), is(true));

    assertThat("title is taken from instance",
      fetchedRequest.getJsonObject("item").getString("title"),
      is("A new instance title"));
  }

  @Test
  void titlesComeFromMultipleInstancesForMultipleRequests() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.james()))
      .getId();

    checkOutFixture.checkOutByBarcode(temeraire);

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .forItem(temeraire)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.james()))
      .getId();

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
