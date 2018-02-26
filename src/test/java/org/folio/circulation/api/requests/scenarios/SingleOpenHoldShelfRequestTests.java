package org.folio.circulation.api.requests.scenarios;

import org.folio.circulation.api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.circulation.api.support.builders.RequestBuilder.CLOSED_FILLED;
import static org.folio.circulation.api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class SingleOpenHoldShelfRequestTests extends APITests {
  @Test
  public void statusChangesToAwaitingPickupWhenItemCheckedIn()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJames);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));
  }

  @Test
  public void statusChangesToFulfilledWhenItemCheckedOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    Response item = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(item.getStatusCode(), is(HTTP_OK));
    assertThat(item.getJson().getJsonObject("status").getString("name"), is("Checked out"));
  }

  @Test
  public void itemCannotBeCheckedOutToOtherPatronWhenRequestIsAwaitingPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJames);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("message"),
      is("User checking out must be requester awaiting pickup"));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));
  }

  @Test
  public void checkingInLoanThatFulfilsRequestShouldMakeItemAvailable()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJames);

    final IndividualResource loanToJessica = loansFixture.checkOut(smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJessica);

    Response item = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(item.getStatusCode(), is(HTTP_OK));
    assertThat(item.getJson().getJsonObject("status").getString("name"), is("Available"));
  }

  @Test
  public void closedRequestShouldNotAffectFurtherLoans()
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
      smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJames);

    final IndividualResource loanToJessica = loansFixture.checkOut(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    loansFixture.checkIn(loanToJessica);

    loansFixture.checkOut(smallAngryPlanet, steve);

    Response item = itemsClient.getById(smallAngryPlanet.getId());

    assertThat(item.getStatusCode(), is(HTTP_OK));
    assertThat(item.getJson().getJsonObject("status").getString("name"), is("Checked out"));
  }

}
