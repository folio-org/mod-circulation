package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class MultipleHoldShelfRequestsTests extends APITests {

  @Test
  public void statusOfOldestRequestChangesToAwaitingPickupWhenItemCheckedIn() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  @Parameters({
    "Hold|Checked out",
    "Recall|Checked out"
  })
  public void statusOfOldestHoldAndRecallRequestsChangeToFulfilledWhenItemCheckedOutToRequester(
    String requestType,
    String itemStatus) {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC), requestType);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(itemStatus));
  }

  @Test
  public void checkingInLoanThatFulfilsRequestShouldMakeItemAvailableForPickupToNextRequester() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(CLOSED_FILLED));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void itemCannotBeCheckedOutToOtherPatronWhenOldestRequestIsAwaitingPickup() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasParameter("userBarcode", rebecca.getBarcode()))));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void itemCannotBeCheckedOutToOtherRequesterWhenOldestRequestIsAwaitingPickup() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, steve);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Jones, Steven " +
        "because it has been requested by another patron"),
      hasParameter("userBarcode", steve.getBarcode()))));

    requestByJessica = requestsClient.get(requestByJessica);

    assertThat(requestByJessica.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    requestBySteve = requestsClient.get(requestBySteve);

    assertThat(requestBySteve.getJson().getString("status"), is(OPEN_NOT_YET_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }
}
