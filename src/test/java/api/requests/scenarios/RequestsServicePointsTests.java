package api.requests.scenarios;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.lang.invoke.MethodHandles;

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
}

