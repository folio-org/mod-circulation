package api.requests;

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
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPITitleTests extends APITests {

  @Test
  public void titleIsFromInstanceWhenCreatingRequestWithHoldingAndInstance()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
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
  public void titleIsChangedWhenRequestUpdatedAndInstanceTitleChanged()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
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
  public void titlesComeFromMultipleInstancesForMultipleRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();

    loansFixture.checkOutByBarcode(smallAngryPlanet);

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
      .by(usersFixture.james()))
      .getId();

    loansFixture.checkOutByBarcode(temeraire);

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .forItem(temeraire)
      .withPickupServicePointId(servicePointsFixture.cd1().getId())
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
