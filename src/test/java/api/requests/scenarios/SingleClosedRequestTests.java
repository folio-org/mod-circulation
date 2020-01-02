package api.requests.scenarios;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static org.folio.HttpStatus.HTTP_OK;
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
  public void closedRequestDoesNotStopCheckOutToRequester() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void closedRequestDoesNotStopCheckOutToOtherPatron() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfilToHoldShelf()
      .withRequestDate(new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withRequesterId(jessica.getId()));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    Response getByIdResponse = requestsClient.getById(requestByJessica.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    assertThat(getByIdResponse.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }
}
