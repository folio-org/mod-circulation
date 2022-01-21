package api.loans.scenarios;

import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getRequestContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getRequesterContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getTransitContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.servicePointNameMatcher;
import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.UUIDMatcher.is;
import static java.time.ZoneOffset.UTC;
import static org.folio.HttpStatus.HTTP_OK;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.matchers.JsonObjectMatcher;
import api.support.matchers.TextDateTimeMatcher;
import io.vertx.core.json.JsonObject;

class ServicePointCheckInTests extends APITests {
  @Test
  void isPlacedAwaitingPickupWhenCheckedInAtPickupServicePoint() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final ItemResource nod = itemsFixture.basedUponNod();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.place(
      new RequestBuilder()
        .hold()
        .forItem(nod)
        .by(jessica)
        .withPickupServicePoint(checkInServicePoint)
        .withRequestDate(ZonedDateTime.of(2019, 7, 5, 10, 0, 0, 0, UTC))
        .withRequestExpiration(LocalDate.of(2019, 7, 11)));

    final ZonedDateTime beforeCheckIn = ClockUtil.getZonedDateTime();

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is awaiting pickup",
      itemRepresentation.getJsonObject("status").getString("name"), is("Awaiting pickup"));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is awaiting pickup",
      updatedNod.getJsonObject("status").getString("name"), is("Awaiting pickup"));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is awaiting pickup",
      storedLoan.getString("itemStatus"), is("Awaiting pickup"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(checkInServicePoint.getId()));

    final Response getByIdResponse = requestsClient.getById(request.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    final JsonObject storedRequest = getByIdResponse.getJson();

    assertThat("request status snapshot in storage is open - awaiting pickup",
        storedRequest.getString("status"), is("Open - Awaiting pickup"));

    IndividualResource requestAfterCheckIn = requestsClient.get(request.getId());
    Map<String, Matcher<String>> staffSlipContextMatchers = new HashMap<>();
    staffSlipContextMatchers.putAll(getItemContextMatchers(nod, true));
    staffSlipContextMatchers.putAll(getRequesterContextMatchers(jessica));
    staffSlipContextMatchers.putAll(getRequestContextMatchers(requestAfterCheckIn));
    staffSlipContextMatchers.put("request.requestID", is(request.getId()));
    staffSlipContextMatchers.put("item.lastCheckedInDateTime",
      TextDateTimeMatcher.withinSecondsAfter(2, beforeCheckIn));

    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();
    assertThat(staffSlipContext, JsonObjectMatcher.allOfPaths(staffSlipContextMatchers));
  }

  @Test
  void isPlacedInTransitWhenCheckedInAtAlternatePickupServicePoint() {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource requestServicePoint = servicePointsFixture.cd2();
    final IndividualResource primaryServicePoint = servicePointsFixture.cd3();

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource homeLocation =
        locationsFixture.basedUponExampleLocation(builder -> builder
          .servedBy(primaryServicePoint.getId())
          .withPrimaryServicePoint(primaryServicePoint.getId()));

    final ItemResource nod = itemsFixture.basedUponNod(builder ->
      builder.withPermanentLocation(homeLocation.getId()));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeItemLevelHoldShelfRequest(nod, jessica,
      ClockUtil.getZonedDateTime(), requestServicePoint.getId());

    final ZonedDateTime beforeCheckIn = ClockUtil.getZonedDateTime();

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is in transit",
      itemRepresentation.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item should have a destination",
        itemRepresentation.getString("inTransitDestinationServicePointId"),
        is(requestServicePoint.getId()));

    assertThat("in transit item should have a extended destination properties",
        itemRepresentation.containsKey("inTransitDestinationServicePoint"), is(true));

    final JsonObject destinationServicePoint =
        itemRepresentation.getJsonObject("inTransitDestinationServicePoint");

    assertThat("extended destination properties should include id",
        destinationServicePoint.getString("id"), is(requestServicePoint.getId()));

    assertThat("extended destination properties should include name",
        destinationServicePoint.getString("name"), is("Circ Desk 2"));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is in transit",
      updatedNod.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item in storage should have a destination",
        updatedNod.getString("inTransitDestinationServicePointId"),
        is(requestServicePoint.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is in transit",
      storedLoan.getString("itemStatus"), is("In transit"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(checkInServicePoint.getId()));

    final Response getByIdResponse = requestsClient.getById(request.getId());

    assertThat(getByIdResponse, hasStatus(HTTP_OK));

    assertThat("request status snapshot in storage is open - in transit",
        getByIdResponse.getJson().getString("status"), is("Open - In transit"));

    Map<String, Matcher<String>> staffSlipContextMatchers = new HashMap<>();
    staffSlipContextMatchers.putAll(getItemContextMatchers(nod, true));
    staffSlipContextMatchers.putAll(getTransitContextMatchers(checkInServicePoint, requestServicePoint));
    staffSlipContextMatchers.putAll(getRequesterContextMatchers(jessica));
    staffSlipContextMatchers.put("request.servicePointPickup", servicePointNameMatcher(requestServicePoint));
    staffSlipContextMatchers.put("request.requestID", is(request.getId()));
    staffSlipContextMatchers.put("item.lastCheckedInDateTime",
      TextDateTimeMatcher.withinSecondsAfter(2, beforeCheckIn));

    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();
    assertThat(staffSlipContext, JsonObjectMatcher.allOfPaths(staffSlipContextMatchers));
  }
}
