package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_DELIVERY;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_DELIVERY;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;

public class MultipleMixedFulfilmentRequestsTests extends APITests {

  private static final DateTime DATE_TIME_2017 = new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC);
  private static final DateTime DATE_TIME_2018 = new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC);

  @Test
  public void itemCantBeCheckedOutToAnotherRequesterWhenStatusIsAwaitingDelivery()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, jessica);
    assertResponseContainsItemCantBeCheckedOutError(jessica, response);

    assertRequestHasStatus(deliveryRequestByRebecca, OPEN_AWAITING_DELIVERY);

    assertRequestHasStatus(requestByJessica, OPEN_NOT_YET_FILLED);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }

  @Test
  public void deliveryRequestsAreProcessedNextWhenCheckingInLoanThatFulfilsRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    assertRequestHasStatus(requestByJessica, CLOSED_FILLED);

    assertRequestHasStatus(deliveryRequestByRebecca, OPEN_AWAITING_DELIVERY);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }

  @Test
  public void itemCanBeCheckedOutToDeliveryRequestRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    assertRequestHasStatus(requestByRebecca, CLOSED_FILLED);

    assertRequestHasStatus(requestByJessica, OPEN_NOT_YET_FILLED);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  private void assertResponseContainsItemCantBeCheckedOutError(IndividualResource user, Response response) {
    assertThat(response.getStatusCode(), equalTo(422));
    assertThat(response.getBody(), containsString("cannot be checked out to user"));
    assertThat(response.getBody(), containsString("because it has been requested by another patron"));
    assertThat(response.getJson(), hasErrorWith(hasParameter("userBarcode", user.getBarcode())));
  }

  private void assertRequestHasStatus(IndividualResource deliveryRequest, String expectedStatus) throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    deliveryRequest = requestsClient.get(deliveryRequest);
    assertThat(deliveryRequest.getJson().getString("status"), is(expectedStatus));
  }
}
