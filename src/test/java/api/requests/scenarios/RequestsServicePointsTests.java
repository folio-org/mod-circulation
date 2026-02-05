package api.requests.scenarios;

import static java.time.ZoneOffset.UTC;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.UUID;

import api.support.CheckInByBarcodeResponse;
import api.support.http.ItemResource;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.requests.RequestsAPICreationTests;
import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class RequestsServicePointsTests extends APITests {

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  void pagedRequestCheckedInAtIntendedServicePointTest() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource servicePoint = servicePointsFixture.cd1();

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestItem = firstRequest.getJson().getJsonObject("item");
    assertThat(requestItem.getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(firstRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    checkInFixture.checkInByBarcode(smallAngryPlanet, ClockUtil.getZonedDateTime(), servicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_AWAITING_PICKUP.getValue()));
  }

  @Test
  void pagedRequestForItemWithIntransitStatusCheckedInAtIntendedServicePointTest() {

    //setup item in IN_TRANSIT status
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource inTransitItem = RequestsAPICreationTests.setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, checkInFixture);

    //now, check in at intended service point.
    checkInFixture.checkInByBarcode(inTransitItem, ClockUtil.getZonedDateTime(), requestPickupServicePoint.getId());
    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(inTransitItem);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_AWAITING_PICKUP.getValue()));
  }

  @Test
  void pagedRequestCheckedInAtUnIntendedServicePointTest() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource pickupServicePoint = servicePointsFixture.cd2();

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestItem = firstRequest.getJson().getJsonObject("item");
    assertThat(requestItem.getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(firstRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    log.info("requestServicePoint" + requestPickupServicePoint.getId());
    log.info("pickupServicePoint" + pickupServicePoint.getId());

    checkInFixture.checkInByBarcode(smallAngryPlanet, ClockUtil.getZonedDateTime(), pickupServicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_IN_TRANSIT.getValue()));
  }

  @Test
  void multipleRequestsRetainServicePointInformation() {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource cd1ServicePoint = servicePointsFixture.cd1();
    final IndividualResource cd2ServicePoint = servicePointsFixture.cd2();

    final IndividualResource request1 = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(cd1ServicePoint.getId())
      .by(usersFixture.james()));

    final IndividualResource request2 = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(interestingTimes)
      .withPickupServicePointId(cd2ServicePoint.getId())
      .by(usersFixture.charlotte()));

    checkInFixture.checkInByBarcode(smallAngryPlanet, ClockUtil.getZonedDateTime(), cd1ServicePoint.getId());
    checkInFixture.checkInByBarcode(interestingTimes, ClockUtil.getZonedDateTime(), cd2ServicePoint.getId());

    JsonObject request1AfterCheckIn = requestsClient.getById(request1.getId()).getJson();
    JsonObject request2AfterCheckIn = requestsClient.getById(request2.getId()).getJson();

    assertThat(request1AfterCheckIn.getJsonObject("item").containsKey("retrievalServicePointName"), is(true));
    assertThat(request2AfterCheckIn.getJsonObject("item").containsKey("retrievalServicePointName"), is(true));
    assertThat(request1AfterCheckIn.getJsonObject("pickupServicePoint").getString("name"), is("Circ Desk 1"));
    assertThat(request2AfterCheckIn.getJsonObject("pickupServicePoint").getString("name"), is("Circ Desk 2"));
  }

  @Test
  void requestPickupServicePointIsNotEnrichedWhenServicePointNotFoundInSystem() {

    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet();
    final UUID validServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(item)
      .by(usersFixture.steve())
      .withPickupServicePointId(validServicePointId));

    final UUID nonExistentServicePointId = UUID.randomUUID();
    final JsonObject requestJson = requestsStorageClient.getById(request.getId()).getJson();
    requestJson.put("pickupServicePointId", nonExistentServicePointId.toString());
    requestsStorageClient.replace(request.getId(), requestJson);

    final ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);

    final CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(
      item, checkInDate, validServicePointId);

    assertNotNull(response);

    final JsonObject updatedRequest = requestsStorageClient.getById(request.getId()).getJson();
    assertThat(updatedRequest.getString("pickupServicePointId"),
      is(nonExistentServicePointId.toString()));
    assertThat(updatedRequest.containsKey("pickupServicePoint"), is(false));
  }

  @Test
  void requestPrimaryServicePointIsNotEnrichedWhenServicePointNotFoundInSystem() {

    final UUID validPrimaryServicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource location = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(validPrimaryServicePointId));
    final ItemResource item = itemsFixture.basedUponSmallAngryPlanet(
      builder -> builder.withPermanentLocation(location.getId()));

    final IndividualResource request = requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(item)
      .by(usersFixture.steve())
      .withPickupServicePointId(validPrimaryServicePointId));

    final UUID nonExistentServicePointId = UUID.randomUUID();
    final JsonObject locationJson = locationsClient.getById(location.getId()).getJson();
    locationJson.put("primaryServicePoint", nonExistentServicePointId.toString());
    locationsClient.replace(location.getId(), locationJson);

    final ZonedDateTime checkInDate = ZonedDateTime.of(2019, 10, 10, 12, 30, 0, 0, UTC);
    final CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(
      item, checkInDate, validPrimaryServicePointId);

    assertNotNull(response);

    final JsonObject updatedRequest = requestsStorageClient.getById(request.getId()).getJson();
    assertNotNull(updatedRequest);
    assertThat(updatedRequest.getString("status"), is(RequestStatus.OPEN_AWAITING_PICKUP.getValue()));

    final JsonObject locationFromStorage = locationsClient.getById(location.getId()).getJson();
    assertThat(locationFromStorage.getString("primaryServicePoint"),
      is(nonExistentServicePointId.toString()));
  }
}

