package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.AWAITING_PICKUP;
import static api.support.builders.ItemBuilder.CHECKED_OUT;
import static api.support.builders.RequestBuilder.CLOSED_FILLED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.http.ResourceClient.forRequestsStorage;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.HttpStatus.HTTP_OK;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

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
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

public class SingleOpenHoldShelfRequestTests extends APITests {
  @Test
  public void statusChangesToAwaitingPickupWhenItemCheckedIn() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(request.getJson().getInteger("position"), is(1));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void statusChangesToFulfilledWhenItemCheckedOutToRequester() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

      assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void itemCannotBeCheckedOutToOtherPatronWhenRequestIsAwaitingPickup() {

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
      hasParameter("userBarcode", rebecca.getBarcode()))));

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AWAITING_PICKUP));
  }

  @Test
  public void checkingInLoanThatFulfilsRequestShouldMakeItemAvailable() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    requestsFixture.placeHoldShelfRequest(smallAngryPlanet, jessica,
      new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkInByBarcode(smallAngryPlanet);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(AVAILABLE));
  }

  @Test
  public void closedRequestShouldNotAffectFurtherLoans() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    Response request = requestsClient.getById(requestByJessica.getId());

    assertThat(request, hasStatus(HTTP_OK));

    assertThat(request.getJson().getString("status"), is(CLOSED_FILLED));

    loansFixture.checkInByBarcode(smallAngryPlanet);

    loansFixture.checkOutByBarcode(smallAngryPlanet, steve);

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);

    assertThat(smallAngryPlanet, hasItemStatus(CHECKED_OUT));
  }

  @Test
  public void itemCannotBeCheckedInWhenRequestIsMissingPickupServicePoint() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    final IndividualResource holdRequest = requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(smallAngryPlanet)
      .by(jessica)
      .withRequestDate(new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC))
      .withPickupServicePoint(servicePointsFixture.cd1()));

    removeServicePoint(holdRequest.getId());

    Response response = loansFixture.attemptCheckInByBarcode(new CheckInByBarcodeRequestBuilder()
        .forItem(smallAngryPlanet)
        .at(checkInServicePoint.getId()));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Failed to check in item due to the highest priority request missing a pickup service point")));
  }

  private void removeServicePoint(UUID requestId) {

    final ResourceClient requestsStorage = forRequestsStorage();

    final Response fetchedRequest = requestsStorage.getById(requestId);

    final JsonObject holdRequestWithoutPickupServicePoint = fetchedRequest.getJson();

    holdRequestWithoutPickupServicePoint.remove("pickupServicePointId");

    requestsStorage.replace(requestId, holdRequestWithoutPickupServicePoint);
  }
}
