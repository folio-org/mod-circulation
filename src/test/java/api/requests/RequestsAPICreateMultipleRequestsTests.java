package api.requests;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import org.folio.circulation.support.http.client.IndividualResource;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class RequestsAPICreateMultipleRequestsTests extends APITests {

  @Test
  public void canCreateMultipleRequestsOfSameTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.steve());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.jessica())
      .create());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca())
      .create());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());
  }

  @Test
  public void canCreateMultipleRequestsOfDifferentTypeForSameItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    loansFixture.checkOut(smallAngryPlanet, usersFixture.rebecca());

    requestsClient.create(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(usersFixture.james())
      .create());

    requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .by(usersFixture.charlotte())
      .create());

    requestsClient.create(new RequestBuilder()
      .recall()
      .forItem(smallAngryPlanet)
      .by(usersFixture.steve())
      .create());
  }
}
