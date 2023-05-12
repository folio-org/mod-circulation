package api.requests.scenarios;

import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static java.time.ZoneOffset.UTC;
import static org.folio.HttpStatus.HTTP_OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;

class SingleClosedRequestTests extends APITests {
  @Test
  void closedRequestDoesNotStopCheckOutToRequester() {

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfillToHoldShelf()
      .withRequestDate(ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequesterId(jessica.getId()));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    IndividualResource checkedOutSmallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(checkedOutSmallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  void closedRequestDoesNotStopCheckOutToOtherPatron() {

    ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsClient.create(new RequestBuilder()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .fulfillToHoldShelf()
      .withRequestDate(ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC))
      .fulfilled() //TODO: Replace with closed cancelled when introduced
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequesterId(jessica.getId()));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, steve);

    Response getByIdResponse = requestsClient.getById(requestByJessica.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    assertThat(getByIdResponse.getJson().getString("status"), is(CLOSED_FILLED));

    IndividualResource checkedOutSmallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(checkedOutSmallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }
}
