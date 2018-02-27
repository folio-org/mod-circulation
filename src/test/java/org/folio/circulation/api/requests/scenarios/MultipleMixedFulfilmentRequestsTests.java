package org.folio.circulation.api.requests.scenarios;

import org.folio.circulation.api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Ignore;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.builders.RequestBuilder.*;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class MultipleMixedFulfilmentRequestsTests extends APITests {
  @Test
  @Ignore
  public void statusOfOldestRequestChangesToAwaitingPickupWhenItemCheckedIn()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
  }

  @Test
  @Ignore
  public void statusOfOldestRequestChangesToFulfilledWhenItemCheckedOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));
  }

  @Test
  @Ignore
  public void itemCannotBeCheckedOutToOtherPatronWhenOldestRequestIsAwaitingPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("message"),
      is("User checking out must be requester awaiting pickup"));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  @Test
  @Ignore
  public void itemCannotBeCheckedOutToOtherRequesterWhenOldestRequestIsAwaitingPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, steve);

    assertThat(response.getJson().getString("message"),
      is("User checking out must be requester awaiting pickup"));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  @Test
  @Ignore
  public void checkingInLoanThatFulfilsRequestShouldMakeItemAvailableForPickupToNextRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    IndividualResource loanToJessica = loansFixture.checkOut(smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJessica);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    //TODO: Should be awaiting pickup when CIRC-52 is completed
    assertThat(smallAngryPlanet.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }
}
