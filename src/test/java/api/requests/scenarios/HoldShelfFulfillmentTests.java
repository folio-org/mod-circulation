package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.IN_TRANSIT;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_IN_TRANSIT;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.UUIDMatcher.is;
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

public class HoldShelfFulfillmentTests extends APITests {

  @Test
  public void canBePickedUpWhenCheckedInAtPickupServicePoint()
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
  public void canBePickedUpWhenCheckedInAtPickupServicePointAfterTransit()
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
}
