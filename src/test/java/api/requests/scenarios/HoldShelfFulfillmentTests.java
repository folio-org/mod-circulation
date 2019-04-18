package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.ItemBuilder.IN_TRANSIT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static api.support.matchers.CheckOutByBarcodeResponseMatchers.hasUserBarcodeParameter;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;

public class HoldShelfFulfillmentTests extends APITests {
  @Test
  public void itemIsReadyForPickUpWhenCheckedInAtPickupServicePoint()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC),
      pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));

    assertThat("awaiting pickup item should not have a destination",
      smallAngryPlanet.getJson().containsKey("inTransitDestinationServicePointId"),
      is(false));
  }

  @Test
  public void canBeCheckedOutToRequestingPatronWhenReadyForPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica,
      new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC),
      pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void checkInAtDifferentServicePointPlacesItemInTransit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC),
      pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_IN_TRANSIT));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(IN_TRANSIT));

    final String destinationServicePoint = smallAngryPlanet.getJson()
      .getString("inTransitDestinationServicePointId");

    assertThat("in transit item should have a destination",
      destinationServicePoint, is(pickupServicePoint.getId()));
  }

  @Test
  public void canBeCheckedOutToRequestingPatronWhenInTransit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica,
      new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC),
      pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void itemIsReadyForPickUpWhenCheckedInAtPickupServicePointAfterTransit()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource pickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC),
      pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), checkInServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,
      DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));

    assertThat("awaiting pickup item should not have a destination",
      smallAngryPlanet.getJson().containsKey("inTransitDestinationServicePointId"),
      is(false));
  }

  @Test
  public void cannotCheckOutToOtherPatronWhenRequestIsAwaitingPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasUserBarcodeParameter(rebecca))));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void cannotCheckOutToOtherPatronWhenRequestIsInTransitForPickup()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkInServicePoint = servicePointsFixture.cd2();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC),
      requestServicePoint.getId());

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .at(checkInServicePoint.getId()));

    Response response = loansFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);

    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("The Long Way to a Small, Angry Planet (Barcode: 036000291452) " +
        "cannot be checked out to user Stuart, Rebecca " +
        "because it has been requested by another patron"),
      hasUserBarcodeParameter(rebecca))));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(OPEN_IN_TRANSIT));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(IN_TRANSIT));
  }
}
