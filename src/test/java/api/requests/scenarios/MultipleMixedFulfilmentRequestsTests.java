package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AWAITING_DELIVERY;
import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_DELIVERY;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.builders.RequestBuilder.OPEN_NOT_YET_FILLED;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.time.ZonedDateTime;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.http.IndividualResource;
import api.support.http.UserResource;
import lombok.val;

class MultipleMixedFulfillmentRequestsTests extends APITests {
  private static final ZonedDateTime DATE_TIME_2017 = ZonedDateTime.of(2017, 7, 22, 10, 22, 54, 0, UTC);
  private static final ZonedDateTime DATE_TIME_2018 = ZonedDateTime.of(2018, 1, 10, 15, 34, 21, 0, UTC);

  @Test
  void itemCantBeCheckedOutToAnotherRequesterWhenStatusIsAwaitingDelivery() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    val steve = usersFixture.steve();
    val rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeItemLevelDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    Response response = checkOutFixture.attemptCheckOutByBarcode(smallAngryPlanet, jessica);
    assertResponseContainsItemCantBeCheckedOutError(jessica, response);

    assertRequestHasStatus(deliveryRequestByRebecca, OPEN_AWAITING_DELIVERY);

    assertRequestHasStatus(requestByJessica, OPEN_NOT_YET_FILLED);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }

  @Test
  void deliveryRequestIsProcessedWhenItIsNextInQueueAndItemCheckedIn() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    val steve = usersFixture.steve();
    val rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeItemLevelDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    assertRequestHasStatus(requestByJessica, CLOSED_FILLED);

    assertRequestHasStatus(deliveryRequestByRebecca, OPEN_AWAITING_DELIVERY);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_DELIVERY));
  }

  @Test
  void holdShelfRequestIsProcessedWhenItIsNextInQueueAndItemCheckedIn() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    val steve = usersFixture.steve();
    val rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource deliveryRequestByRebecca = requestsFixture.placeItemLevelDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2018);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    assertRequestHasStatus(requestByJessica, OPEN_AWAITING_PICKUP);

    assertRequestHasStatus(deliveryRequestByRebecca, OPEN_NOT_YET_FILLED);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  void itemCanBeCheckedOutToDeliveryRequestRequester() {
    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    val steve = usersFixture.steve();
    val rebecca = usersFixture.rebecca();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByRebecca = requestsFixture.placeItemLevelDeliveryRequest(
      smallAngryPlanet, rebecca, DATE_TIME_2017);

    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, DATE_TIME_2017);

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, DATE_TIME_2018);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, rebecca);

    assertRequestHasStatus(requestByRebecca, CLOSED_FILLED);

    assertRequestHasStatus(requestByJessica, OPEN_NOT_YET_FILLED);

    assertRequestHasStatus(requestBySteve, OPEN_NOT_YET_FILLED);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  private void assertResponseContainsItemCantBeCheckedOutError(UserResource user, Response response) {
    assertThat(response.getStatusCode(), equalTo(422));
    assertThat(response.getBody(), containsString("cannot be checked out to user"));
    assertThat(response.getBody(), containsString("because it has been requested by another patron"));
    assertThat(response.getJson(), hasErrorWith(hasParameter("userBarcode", user.getBarcode())));
  }

  private void assertRequestHasStatus(IndividualResource deliveryRequest, String expectedStatus) {
    deliveryRequest = requestsClient.get(deliveryRequest);
    assertThat(deliveryRequest.getJson().getString("status"), is(expectedStatus));
  }
}
