package api.requests;

import static api.support.http.InterfaceUrls.requestsUrl;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;

public class RequestsAPIDeletionTests extends APITests {
  @Test
  public void canDeleteAllRequests() {
    UUID requesterId = usersFixture.rebecca().getId();

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);
    loansFixture.checkOutByBarcode(smallAngryPlanet);
    loansFixture.checkOutByBarcode(temeraire);

    requestsFixture.place(new RequestBuilder()
      .withItemId(nod.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    requestsFixture.place(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    requestsFixture.place(new RequestBuilder()
      .withItemId(temeraire.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    Response deleteResponse = requestsFixture.deleteAllRequests();

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(0));
    assertThat(allRequests.totalRecords(), is(0));
  }

  @Test
  public void canDeleteAnIndividualRequest() {
    UUID requesterId = usersFixture.rebecca().getId();

    final InventoryItemResource nod = itemsFixture.basedUponNod();
    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(nod);
    loansFixture.checkOutByBarcode(smallAngryPlanet);
    loansFixture.checkOutByBarcode(temeraire);

    final UUID firstRequestId = requestsFixture.place(new RequestBuilder()
      .withItemId(nod.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    final UUID secondRequestId = requestsFixture.place(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    final UUID thirdRequestId = requestsFixture.place(new RequestBuilder()
      .withItemId(temeraire.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId))
      .getId();

    CompletableFuture<Response> deleteCompleted = new CompletableFuture<>();

    requestsFixture.deleteRequest(secondRequestId);

    assertThat(requestsFixture.getById(firstRequestId).getStatusCode(),
      is(HTTP_OK));

    assertThat(requestsFixture.getById(secondRequestId).getStatusCode(),
      is(HTTP_NOT_FOUND));

    assertThat(requestsFixture.getById(thirdRequestId).getStatusCode(),
      is(HTTP_OK));

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(2));
    assertThat(allRequests.totalRecords(), is(2));
  }
}
