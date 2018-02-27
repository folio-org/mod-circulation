package org.folio.circulation.api.requests.scenarios;

import org.folio.circulation.api.support.APITests;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.folio.circulation.api.support.builders.RequestBuilder.*;
import static org.folio.circulation.api.support.matchers.ItemStatusCodeMatcher.hasStatus;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class MultipleMixedFulfilmentRequestsTests extends APITests {
  @Test
  public void deliveryRequestIsIgnoredWhenItemCheckedIn()
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

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 11, 15, 13, 35, 21, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasStatus("Available"));
  }

  @Test
  public void deliveryRequestIsIgnoredWhenItemItemCheckedOutToRequester()
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

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    //TODO: Come back to this to fix status change on check out
//    assertThat(smallAngryPlanet, hasStatus("Checked out - held"));
  }

  @Test
  public void deliveryRequestIsIgnoredWhenItemCannotBeCheckedOutToOtherPatron()
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

    IndividualResource requestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, rebecca);

    assertThat(response.getJson().getString("message"),
      is("User checking out must be requester awaiting pickup"));

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasStatus("Available"));
  }

  @Test
  public void deliveryRequestIsIgnoredWhenItemCannotBeCheckedOutToOtherRequester()
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

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, steve);

    assertThat(response.getJson().getString("message"),
      is("User checking out must be requester awaiting pickup"));

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet.getJson().getJsonObject("status").getString("name"),
      is("Available"));
  }

  //TODO: Add second delivery request in between fulfilled and next request
  @Test
  public void deliveryRequestsAreIgnoredWhenCheckingInLoanThatFulfilsRequest()
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

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkIn(loanToJames);

    IndividualResource loanToJessica = loansFixture.checkOut(smallAngryPlanet, jessica);

    loansFixture.checkIn(loanToJessica);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

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
