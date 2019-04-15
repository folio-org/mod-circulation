package api.requests.scenarios;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;

public class RequestsForDifferentItemsTests extends APITests {
  @Test
  public void requestsCreatedForDifferentItemsAreInDifferentQueues()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource nod = itemsFixture.basedUponNod();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);
    loansFixture.checkOutByBarcode(nod, steve);

    final IndividualResource firstRequestForSmallAngryPlanet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.jessica())
      .create());

    final IndividualResource firstRequestForNod = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(nod)
      .by(usersFixture.rebecca())
      .create());

    final IndividualResource secondRequestForNod = requestsClient.create(
      new RequestBuilder()
        .hold()
        .withPickupServicePointId(pickupServicePointId)
        .forItem(nod)
        .by(usersFixture.james())
        .create());

    final IndividualResource secondRequestForSmallAngryPlannet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    assertThat(firstRequestForSmallAngryPlanet.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForSmallAngryPlannet.getJson().getInteger("position"), is(2));
    assertThat(firstRequestForNod.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForNod.getJson().getInteger("position"), is(2));
  }
}
