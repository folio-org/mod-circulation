package api.requests.scenarios;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;

public class SingleClosedRequestTests extends APITests {
  @Test
  public void closedRequestDoesNotStopCheckOutToRequester()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOut(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void closedRequestDoesNotStopCheckOutToOtherPatron()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource loanToJames = loansFixture.checkOut(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOut(smallAngryPlanet, steve);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }
}
