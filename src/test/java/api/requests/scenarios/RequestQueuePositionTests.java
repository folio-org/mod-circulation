package api.requests.scenarios;

import api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

//TODO: Maybe move these tests to scenarios which better describe the situation
public class RequestQueuePositionTests extends APITests {
  @Test
  public void cancelledRequestShouldBeRemovedFromQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, new DateTime(2018, 2, 4, 7, 4, 53, DateTimeZone.UTC));

    requestsFixture.cancelRequest(requestBySteve);

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat("Should not have a position",
      requestBySteve.getJson().containsKey("position"), is(false));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getInteger("position"), is(1));

    requestByCharlotte = requestsClient.get(requestByCharlotte);

    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));
  }

  @Test
  public void fulfilledRequestShouldBeRemovedFromQueue()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    final IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, new DateTime(2018, 2, 4, 7, 4, 53, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat("Should not have a position",
      requestByJessica.getJson().containsKey("position"), is(false));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getInteger("position"), is(1));

    requestByCharlotte = requestsClient.get(requestByCharlotte);

    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));
  }
}
