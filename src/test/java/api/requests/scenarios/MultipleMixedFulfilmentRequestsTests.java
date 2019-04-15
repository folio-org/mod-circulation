package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.hamcrest.CoreMatchers.allOf;
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

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 11, 15, 13, 35, 21, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
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

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
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

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void itemCannotBeCheckedOutToDeliveryRequestRequester()
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
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it is awaiting pickup by another patron"),
      hasUUIDParameter("userId", rebecca.getId()))));

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
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
    IndividualResource charlotte = usersFixture.charlotte();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, charlotte);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Broadwell, Charlotte " +
        "because it is awaiting pickup by another patron"),
      hasUUIDParameter("userId", charlotte.getId()))));

    requestByRebecca = requestsClient.get(requestByRebecca);

    assertThat(requestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
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

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, rebecca, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOut(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Jones, Steven " +
        "because it is awaiting pickup by another patron"),
      hasUUIDParameter("userId", steve.getId()))));

    deliveryRequestByRebecca = requestsClient.get(deliveryRequestByRebecca);

    assertThat(deliveryRequestByRebecca.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }
}
