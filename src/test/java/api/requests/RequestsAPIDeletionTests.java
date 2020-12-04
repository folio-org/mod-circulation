package api.requests;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.UUID;

import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;

public class RequestsAPIDeletionTests extends APITests {
  @Test
  public void canDeleteAllRequests() {
    UUID requesterId = usersFixture.rebecca().getId();

    final ItemResource nod = itemsFixture.basedUponNod();
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

    requestsFixture.place(new RequestBuilder()
      .withItemId(nod.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .withItemId(smallAngryPlanet.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.place(new RequestBuilder()
      .withItemId(temeraire.getId())
      .withPickupServicePointId(pickupServicePointId)
      .withRequesterId(requesterId));

    requestsFixture.deleteAllRequests();

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(0));
    assertThat(allRequests.totalRecords(), is(0));
  }

  @Test
  public void canDeleteAnIndividualRequest() {
    UUID requesterId = usersFixture.rebecca().getId();

    final ItemResource nod = itemsFixture.basedUponNod();
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

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
