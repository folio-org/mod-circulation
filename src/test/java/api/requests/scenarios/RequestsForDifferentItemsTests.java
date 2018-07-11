package api.requests.scenarios;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsForDifferentItemsTests extends APITests {
  @Test
  public void requestsCreatedForDifferentItemsAreInDifferentQueues()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource nod = itemsFixture.basedUponNod();

    final IndividualResource steve = usersFixture.steve();

    loansFixture.checkOut(smallAngryPlanet, steve);
    loansFixture.checkOut(nod, steve);

    final IndividualResource firstRequestForSmallAngryPlanet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.jessica())
      .create());

    final IndividualResource firstRequestForNod = requestsClient.create(
      new RequestBuilder()
      .hold()
      .forItem(nod)
      .by(usersFixture.rebecca())
      .create());

    final IndividualResource secondRequestForNod = requestsClient.create(
      new RequestBuilder()
        .hold()
        .forItem(nod)
        .by(usersFixture.james())
        .create());

    final IndividualResource secondRequestForSmallAngryPlannet = requestsClient.create(
      new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    assertThat(firstRequestForSmallAngryPlanet.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForSmallAngryPlannet.getJson().getInteger("position"), is(2));
    assertThat(firstRequestForNod.getJson().getInteger("position"), is(1));
    assertThat(secondRequestForNod.getJson().getInteger("position"), is(2));
  }
}
