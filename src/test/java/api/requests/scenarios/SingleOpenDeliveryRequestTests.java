package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.AWAITING_DELIVERY;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_CANCELLED;
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

import api.support.APITests;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;

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

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);

    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
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

    validateRequestStatus(requestByJessica, CLOSED_FILLED);
    validateItemStatus(smallAngryPlanet, CHECKED_OUT);
  }

  @Test
  public void itemCannotBeCheckedOutToAnotherPatron()
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

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
  }

  @Test
  public void itemCanBeCheckedInForSecondTime()
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

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
  }

  @Test
  public void itemBecomesAvailableWhenRequestIsCancelled() throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    requestsFixture.cancelRequest(requestByJessica);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatus(requestByJessica, CLOSED_CANCELLED);
    validateItemStatus(smallAngryPlanet, AVAILABLE);
  }

  private void validateRequestStatus(IndividualResource request, String expectedStatus)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response response = requestsClient.getById(request.getId());

    assertThat(response.getJson().getString("status"), is(expectedStatus));
  }

  private void validateRequestStatusAndPosition(IndividualResource request,
      String expectedStatus, int expectedPosition)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    Response response = requestsClient.getById(request.getId());

    assertThat(response.getJson().getString("status"), is(expectedStatus));
    assertThat(response.getJson().getInteger("position"), is(expectedPosition));
  }

  private void validateItemStatus(IndividualResource item,
        String expectedStatus)
    throws MalformedURLException,
    InterruptedException,
    ExecutionException,
    TimeoutException {

    item = itemsClient.get(item);

    assertThat(item, hasItemStatus(expectedStatus));
  }
}
