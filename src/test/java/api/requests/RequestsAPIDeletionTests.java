package api.requests;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.JsonArrayHelper;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.http.client.ResponseHandler;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InterfaceUrls;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class RequestsAPIDeletionTests extends APITests {

  @Test
  public void canDeleteAllRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID requesterId = usersFixture.rebecca().getId();

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);
    loansFixture.checkOutByBarcode(smallAngryPlanet);
    loansFixture.checkOutByBarcode(temeraire);

    requestsClient.create(new RequestBuilder()
      .withItemId(nod.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    requestsClient.create(new RequestBuilder()
      .withItemId(temeraire.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(getAllResponse.getStatusCode(), is(HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(0));
    assertThat(allRequests.getInteger("totalRecords"), is(0));
  }

  @Test
  public void canDeleteAnIndividualRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    UUID requesterId = usersFixture.rebecca().getId();

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);
    loansFixture.checkOutByBarcode(smallAngryPlanet);
    loansFixture.checkOutByBarcode(temeraire);

    final UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .withItemId(nod.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    final UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    final UUID thirdRequestId = requestsClient.create(new RequestBuilder()
      .withItemId(temeraire.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    client.delete(InterfaceUrls.requestsUrl(String.format("/%s", secondRequestId)),
      ResponseHandler.any(deleteCompleted));

    Response deleteResponse = deleteCompleted.get(5, TimeUnit.SECONDS);

    assertThat(deleteResponse.getStatusCode(), is(HTTP_NO_CONTENT));

    assertThat(requestsClient.getById(firstRequestId).getStatusCode(), is(HTTP_OK));

    assertThat(requestsClient.getById(secondRequestId).getStatusCode(), is(HTTP_NOT_FOUND));

    assertThat(requestsClient.getById(thirdRequestId).getStatusCode(), is(HTTP_OK));

    CompletableFuture<Response> getAllCompleted = new CompletableFuture<>();

    client.get(InterfaceUrls.requestsUrl(), ResponseHandler.any(getAllCompleted));

    Response getAllResponse = getAllCompleted.get(5, TimeUnit.SECONDS);

    assertThat(String.format("Get all requests failed: \"%s\"", getAllResponse.getBody()),
      getAllResponse.getStatusCode(), is(HTTP_OK));

    JsonObject allRequests = getAllResponse.getJson();

    List<JsonObject> requests = getRequests(allRequests);

    assertThat(requests.size(), is(2));
    assertThat(allRequests.getInteger("totalRecords"), is(2));
  }

  private List<JsonObject> getRequests(JsonObject page) {
    return JsonArrayHelper.toList(page.getJsonArray("requests"));
  }
}
