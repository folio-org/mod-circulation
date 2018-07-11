package api.requests;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsAPICreateMultipleRequestsTests extends APITests {

  @Test
  public void canCreateMultipleRequestsOfSameTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.steve());

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.jessica())
      .create());

    final IndividualResource secondRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca())
      .create());

    final IndividualResource thirdRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    assertThat(firstRequest.getJson().getInteger("position"), is(1));
    assertThat(secondRequest.getJson().getInteger("position"), is(2));
    assertThat(thirdRequest.getJson().getInteger("position"), is(3));
  }

  @Test
  public void canCreateMultipleRequestsOfDifferentTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.rebecca());

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.james())
      .create());

    final IndividualResource secondRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    final IndividualResource thirdRequest = requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.steve())
      .create());

    assertThat(firstRequest.getJson().getInteger("position"), is(1));
    assertThat(secondRequest.getJson().getInteger("position"), is(2));
    assertThat(thirdRequest.getJson().getInteger("position"), is(3));
  }
}
