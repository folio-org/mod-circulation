package api.requests;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;

public class RequestsAPIDeletionTests extends APITests {
  @Test
  public void canDeleteARequestFromTheQueue() {
    final var nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final var firstRequestId = requestsFixture.place(requestFor(nod)).getId();
    final var secondRequestId = requestsFixture.place(requestFor(nod)).getId();
    final var thirdRequestId = requestsFixture.place(requestFor(nod)).getId();

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

  @Test
  public void canDeleteAllRequests() {
    final ItemResource nod = itemsFixture.basedUponNod();
    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

    requestsFixture.place(requestFor(nod));
    requestsFixture.place(requestFor(smallAngryPlanet));
    requestsFixture.place(requestFor(temeraire));

    requestsFixture.deleteAllRequests();

    MultipleJsonRecords allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(0));
    assertThat(allRequests.totalRecords(), is(0));
  }

  private RequestBuilder requestFor(ItemResource item) {
    return new RequestBuilder()
      .withItemId(item.getId())
      .withPickupServicePoint(servicePointsFixture.cd1())
      .withRequesterId(usersFixture.rebecca().getId());
  }
}
