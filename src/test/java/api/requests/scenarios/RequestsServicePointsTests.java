package api.requests.scenarios;

import api.requests.RequestsAPICreationTests;
import api.support.APITests;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RequestsServicePointsTests extends APITests {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Test
  public void pagedRequestCheckedInAtIntendedServicePointTest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource servicePoint = servicePointsFixture.cd1();

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(servicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestItem = firstRequest.getJson().getJsonObject("item");
    assertThat(requestItem.getString("status"), is (ItemStatus.PAGED.getValue()));
    assertThat(firstRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    loansFixture.checkInByBarcode(smallAngryPlanet,DateTime.now(DateTimeZone.UTC),servicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_AWAITING_PICKUP.getValue()));
  }

  @Test
  public void pagedRequestForItemWithIntransitStatusCheckedInAtIntendedServicePointTest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //setup item in IN_TRANSIT status
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();

    final IndividualResource inTransitItem = RequestsAPICreationTests.setupItemInTransit(requestPickupServicePoint, servicePointsFixture.cd2(),
      itemsFixture, requestsClient,
      usersFixture, requestsFixture, loansFixture);

    //now, check in at intended service point.
    loansFixture.checkInByBarcode(inTransitItem,DateTime.now(DateTimeZone.UTC),requestPickupServicePoint.getId());
    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(inTransitItem);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.AWAITING_PICKUP.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_AWAITING_PICKUP.getValue()));
  }

  @Test
  public void pagedRequestCheckedInAtUnIntendedServicePointTest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource requestPickupServicePoint = servicePointsFixture.cd1();
    final IndividualResource pickupServicePoint = servicePointsFixture.cd2();

    final IndividualResource firstRequest = requestsClient.create(new RequestBuilder()
      .page()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(requestPickupServicePoint.getId())
      .by(usersFixture.james()));

    JsonObject requestItem = firstRequest.getJson().getJsonObject("item");
    assertThat(requestItem.getString("status"), is ( ItemStatus.PAGED.getValue()));
    assertThat(firstRequest.getJson().getString("status"), is (RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    log.info("requestServicePoint" + requestPickupServicePoint.getId());
    log.info("pickupServicePoint" + pickupServicePoint.getId());

    loansFixture.checkInByBarcode(smallAngryPlanet,DateTime.now(DateTimeZone.UTC),pickupServicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_IN_TRANSIT.getValue()));
  }


  public IndividualResource setupItemInTransit(IndividualResource requestPickupServicePoint, IndividualResource pickupServicePoint)
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    //In order to get the item into the IN_TRANSIT state, for now we need to go the round-about route of delivering it to the unintended pickup location first
    //then check it in at the intended pickup location.
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

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

    //check it it at the "wrong" or unintended pickup location
    loansFixture.checkInByBarcode(smallAngryPlanet, DateTime.now(DateTimeZone.UTC), pickupServicePoint.getId());

    MultipleRecords<JsonObject> requests = requestsFixture.getQueueFor(smallAngryPlanet);
    JsonObject pagedRequestRecord = requests.getRecords().iterator().next();

    assertThat(pagedRequestRecord.getJsonObject("item").getString("status"), is(ItemStatus.IN_TRANSIT.getValue()));
    assertThat(pagedRequestRecord.getString("status"), is(RequestStatus.OPEN_IN_TRANSIT.getValue()));

    return smallAngryPlanet;
  }
}

