package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_DELIVERY;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_DELIVERY;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
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

public class SingleOpenDeliveryRequestTests extends APITests {
  @Test
  public void statusChangesToAwaitingDeliveryWhenItemCheckedIn()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_DELIVERY));
    assertThat(request.getJson().getInteger("position"), is(1));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }

  @Test
  public void requestStatusChangesToFilledWhenItemCheckedOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void itemCantBeCheckedOutToAnotherPatron()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);
    assertThat(response.getStatusCode(), equalTo(422));
    assertThat(response.getBody(), containsString("cannot be checked out to user"));
    assertThat(response.getBody(), containsString("because it has been requested by another patron"));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_DELIVERY));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    //This request is still in the queue (as it cannot currently be fulfilled)
    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }
}
