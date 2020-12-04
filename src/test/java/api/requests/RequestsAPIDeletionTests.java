package api.requests;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.ItemResource;
import api.support.http.UserResource;

public class RequestsAPIDeletionTests extends APITests {
  @Test
  public void canDeleteARequestFromTheQueue() {
    final var nod = itemsFixture.basedUponNod();

    checkOutFixture.checkOutByBarcode(nod);

    final var firstRequest = requestsFixture.place(requestFor(nod, usersFixture.rebecca()));
    final var secondRequest = requestsFixture.place(requestFor(nod, usersFixture.charlotte()));
    final var thirdRequest = requestsFixture.place(requestFor(nod, usersFixture.james()));

    requestsFixture.deleteRequest(secondRequest.getId());

    assertThat(requestsFixture.getById(firstRequest.getId()).getStatusCode(), is(HTTP_OK));
    assertThat(requestsFixture.getById(secondRequest.getId()).getStatusCode(), is(HTTP_NOT_FOUND));
    assertThat(requestsFixture.getById(thirdRequest.getId()).getStatusCode(), is(HTTP_OK));

    final var allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(2));
    assertThat(allRequests.totalRecords(), is(2));
  }

  @Test
  public void canDeleteAllRequests() {
    final var nod = itemsFixture.basedUponNod();
    final var smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final var temeraire = itemsFixture.basedUponTemeraire();

    checkOutFixture.checkOutByBarcode(nod);
    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

    requestsFixture.place(requestFor(nod));
    requestsFixture.place(requestFor(smallAngryPlanet));
    requestsFixture.place(requestFor(temeraire));

    requestsFixture.deleteAllRequests();

    final var allRequests = requestsFixture.getAllRequests();

    assertThat(allRequests.size(), is(0));
    assertThat(allRequests.totalRecords(), is(0));
  }

  private RequestBuilder requestFor(ItemResource item) {
    return requestFor(item, usersFixture.rebecca());
  }

  private RequestBuilder requestFor(ItemResource item, UserResource requester) {
    return new RequestBuilder()
      .withItemId(item.getId())
      .withPickupServicePoint(servicePointsFixture.cd1())
      .withRequesterId(requester.getId());
  }
}
