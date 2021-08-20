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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import api.support.http.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import api.support.APITests;

class SingleOpenDeliveryRequestTests extends APITests {

  @Test
  void statusChangesToAwaitingDeliveryWhenItemCheckedIn() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);

    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
  }

  @Test
  void requestStatusChangesToFilledWhenItemCheckedOutToRequester() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    validateRequestStatus(requestByJessica, CLOSED_FILLED);
    validateItemStatus(smallAngryPlanet, CHECKED_OUT);
  }

  @Test
  void itemCannotBeCheckedOutToAnotherPatron() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, rebecca);
    assertThat(response.getStatusCode(), equalTo(422));
    assertThat(response.getBody(), containsString("cannot be checked out to user"));
    assertThat(response.getBody(), containsString("because it has been requested by another patron"));

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
  }

  @Test
  void itemCanBeCheckedInForSecondTime() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatusAndPosition(requestByJessica, OPEN_AWAITING_DELIVERY, 1);
    validateItemStatus(smallAngryPlanet, AWAITING_DELIVERY);
  }

  @Test
  void itemBecomesAvailableWhenRequestIsCancelled() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeDeliveryRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    requestsFixture.cancelRequest(requestByJessica);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    validateRequestStatus(requestByJessica, CLOSED_CANCELLED);
    validateItemStatus(smallAngryPlanet, AVAILABLE);
  }

  private void validateRequestStatus(IndividualResource request, String expectedStatus) {

    Response response = requestsClient.getById(request.getId());

    assertThat(response.getJson().getString("status"), is(expectedStatus));
  }

  private void validateRequestStatusAndPosition(IndividualResource request,
      String expectedStatus, int expectedPosition) {

    Response response = requestsClient.getById(request.getId());

    assertThat(response.getJson().getString("status"), is(expectedStatus));
    assertThat(response.getJson().getInteger("position"), is(expectedPosition));
  }

  private void validateItemStatus(IndividualResource item,
        String expectedStatus) {

    item = itemsClient.get(item);

    assertThat(item, hasItemStatus(expectedStatus));
  }
}
