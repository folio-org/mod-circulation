package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.fixtures.ConfigurationExample.timezoneConfigurationFor;
import static api.support.matchers.EventMatchers.isValidLoanDueDateChangedEvent;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static java.time.Clock.fixed;
import static java.time.Clock.offset;
import static java.time.Duration.ofDays;
import static java.util.stream.Collectors.groupingBy;
import static org.folio.circulation.domain.EventType.LOAN_DUE_DATE_CHANGED;
import static org.folio.circulation.domain.EventType.LOG_RECORD;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.folio.circulation.domain.representations.logs.LogEventType.REQUEST_MOVED;
import static org.folio.circulation.support.utils.ClockUtil.getClock;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.folio.circulation.support.utils.ClockUtil.setDefaultClock;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

/**
 * @see <a href="https://issues.folio.org/browse/UIREQ-269">UIREQ-269</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-316">CIRC-316</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-333">CIRC-333</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-395">CIRC-395</a>
 */
class MoveRequestTests extends APITests {

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    setDefaultClock();
  }

  @Test
  void canMoveRequestFromOneItemCopyToAnother() {

    final IndividualResource secondFloorEconomics = locationsFixture.secondFloorEconomics();
    final IndividualResource mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();

    final IndividualResource itemCopyA = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("10203040506"));

    final IndividualResource itemCopyB = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("90806050402"));

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();

    assertThat(itemCopyA.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));
    assertThat(itemCopyB.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));

    IndividualResource itemCopyALoan = checkOutFixture.checkOutByBarcode(itemCopyA, james, getZonedDateTime());
    assertThat(itemCopyALoan.getJson().getString("userId"), is(james.getId().toString()));

    assertThat(itemCopyALoan.getJson().getString("itemId"), is(itemCopyA.getId().toString()));

    assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

    IndividualResource pageRequestForItemCopyB = requestsFixture.placeHoldShelfRequest(
      itemCopyB, jessica, getZonedDateTime().minusHours(3), RequestType.PAGE.getValue());

    IndividualResource recallRequestForItemCopyB = requestsFixture.placeHoldShelfRequest(
      itemCopyB, steve, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    IndividualResource holdRequestForItemCopyA = requestsFixture.placeHoldShelfRequest(
      itemCopyA, charlotte, getZonedDateTime().minusHours(1), RequestType.HOLD.getValue());

    assertThat(requestsFixture.getQueueFor(itemCopyA).getTotalRecords(), is(1));
    assertThat(requestsFixture.getQueueFor(itemCopyB).getTotalRecords(), is(2));

    assertThat(pageRequestForItemCopyB.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(pageRequestForItemCopyB.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));

    assertThat(recallRequestForItemCopyB.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(recallRequestForItemCopyB.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));

    assertThat(holdRequestForItemCopyA.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(holdRequestForItemCopyA.getJson().getJsonObject("item").getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));

    IndividualResource moveRecallRequestToItemCopyA = requestsFixture.move(new MoveRequestBuilder(
      recallRequestForItemCopyB.getId(),
      itemCopyA.getId()
    ));

    assertThat(requestsFixture.getQueueFor(itemCopyA).getTotalRecords(), is(2));
    assertThat(requestsFixture.getQueueFor(itemCopyB).getTotalRecords(), is(1));

    assertThat(moveRecallRequestToItemCopyA.getJson().getString("itemId"), is(itemCopyA.getId().toString()));
    assertThat(moveRecallRequestToItemCopyA.getJson().getString("requesterId"), is(steve.getId().toString()));
    assertThat(moveRecallRequestToItemCopyA.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(moveRecallRequestToItemCopyA.getJson().getJsonObject("item").getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(moveRecallRequestToItemCopyA.getJson().getInteger("position"), is(2));
    retainsStoredSummaries(moveRecallRequestToItemCopyA);

    holdRequestForItemCopyA = requestsClient.get(holdRequestForItemCopyA);
    assertThat(holdRequestForItemCopyA.getJson().getString("itemId"), is(itemCopyA.getId().toString()));
    assertThat(holdRequestForItemCopyA.getJson().getString("requesterId"), is(charlotte.getId().toString()));
    assertThat(holdRequestForItemCopyA.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(holdRequestForItemCopyA.getJson().getJsonObject("item").getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(holdRequestForItemCopyA.getJson().getInteger("position"), is(1));
    retainsStoredSummaries(holdRequestForItemCopyA);

    pageRequestForItemCopyB = requestsClient.get(pageRequestForItemCopyB);
    assertThat(pageRequestForItemCopyB.getJson().getString("itemId"), is(itemCopyB.getId().toString()));
    assertThat(pageRequestForItemCopyB.getJson().getString("requesterId"), is(jessica.getId().toString()));
    assertThat(pageRequestForItemCopyB.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));
    assertThat(pageRequestForItemCopyB.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(pageRequestForItemCopyB.getJson().getInteger("position"), is(1));
    retainsStoredSummaries(pageRequestForItemCopyB);

    itemCopyALoan = loansClient.get(itemCopyALoan);
    assertThat(itemCopyALoan.getJson().getString("userId"), is(james.getId().toString()));
    assertThat(itemCopyALoan.getJson().getString("itemId"), is(itemCopyA.getId().toString()));

    assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

    assertThat(itemsClient.get(itemCopyB).getJson().getJsonObject("status").getString("name"), is(ItemStatus.PAGED.getValue()));
  }

  @Test
  void canMoveAShelfHoldRequestToAnAvailableItem() {
    ItemResource smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents("sap"));
    IndividualResource interestingTimes = itemsFixture
      .basedUponInterestingTimes(itemsFixture.addCallNumberStringComponents("it"));

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .hold()
      .fulfilToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .withRequestDate(getZonedDateTime())
      .withRequesterId(jessica.getId())
      .withPatronComments("Patron comments for smallAngryPlanet")
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    // move jessica's hold shelf request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));
    requestHasCallNumberStringProperties(requestByJessica.getJson(), "sap");

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));
    assertThat(requestByJessica.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    assertThat(requestByJessica.getJson().getString("patronComments"),
      is("Patron comments for smallAngryPlanet"));

    retainsStoredSummaries(requestByJessica);
    requestHasCallNumberStringProperties(requestByJessica.getJson(), "it");

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  void canMoveARecallRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime().minusHours(5));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, getZonedDateTime().minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, getZonedDateTime().minusHours(4), RequestType.RECALL.getValue());

    // move steve's recall request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestBySteve.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning on smallAngryPlanet
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByCharlotte);

    requestByRebecca = requestsClient.get(requestByRebecca);
    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));
    assertThat(requestByRebecca.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByRebecca);

    // check positioning on interestingTimes
    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(2));
    assertThat(requestBySteve.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(3));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(2));
  }

  @Test
  void canMoveTwoRequests() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    // check positioning on smallAngryPlanet before moves
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(2));
    assertThat(requestBySteve.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    // move steve's recall request from smallAngryPlanet to interestingTimes
    IndividualResource firstMoveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestBySteve.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      firstMoveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning after first move
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(1));
    assertThat(requestBySteve.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    // move jessica's recall request from smallAngryPlanet to interestingTimes
    IndividualResource secondMoveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      secondMoveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning after second move
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(2));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(1));
    assertThat(requestBySteve.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(2));
  }

  @Test
  void canMoveAHoldShelfRequestLeavingEmptyQueueAndItemStatusChange() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .page()
      .fulfilToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withInstanceId(((ItemResource) smallAngryPlanet).getInstanceId())
      .withRequestDate(getZonedDateTime().minusHours(4))
      .withRequesterId(jessica.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);
    assertThat(smallAngryPlanet, hasItemStatus(PAGED));

    // move jessica's request from smallAngryPlanet to interestingTimes
    requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId(),
      RequestType.HOLD.getValue()
    ));

    smallAngryPlanet = itemsClient.get(smallAngryPlanet);
    assertThat(smallAngryPlanet, hasItemStatus(AVAILABLE));
  }

  @Test
  void canMoveAHoldShelfRequestToAnEmptyQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime());

    // move jessica's hold shelf request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  void canMoveAHoldShelfRequestReorderingDestinationRequestQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime().minusHours(4));

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, getZonedDateTime().minusHours(5));

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, getZonedDateTime().minusHours(3));

    // make requests for interestingTimes
    IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      interestingTimes, rebecca, getZonedDateTime().minusHours(5));

    IndividualResource requestByJames = requestsFixture.placeItemLevelHoldShelfRequest(
      interestingTimes, james, getZonedDateTime().minusHours(1));

    // move jessica's hold shelf request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning on smallAngryPlanet
    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(1));
    assertThat(requestBySteve.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByCharlotte);

    // check positioning on interestingTimes
    requestByRebecca = requestsClient.get(requestByRebecca);
    assertThat(requestByRebecca.getJson().getInteger("position"), is(1));
    assertThat(requestByRebecca.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByRebecca);

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(3));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(2));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(2));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(3));
  }

  @Test
  void canMoveAHoldShelfRequestPreventDisplacingOpenRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime().minusHours(4));

    IndividualResource requestBySteve = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, steve, getZonedDateTime().minusHours(5));

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, getZonedDateTime().minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, rebecca, getZonedDateTime().minusHours(2));

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeItemLevelHoldShelfRequest(
      interestingTimes, james, getZonedDateTime().minusHours(1));

    checkInFixture.checkInByBarcode(interestingTimes);

    // move jessica's hold shelf request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByJessica.getId(),
      interestingTimes.getId()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning on smallAngryPlanet
    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(1));
    assertThat(requestBySteve.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(2));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByCharlotte);

    requestByRebecca = requestsClient.get(requestByRebecca);
    assertThat(requestByRebecca.getJson().getInteger("position"), is(3));
    assertThat(requestByRebecca.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByRebecca);

    // check positioning on interestingTimes
    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getString("status"), is(OPEN_AWAITING_PICKUP));
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(2));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(3));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(2));
  }

  @Test
  void canMoveARecallRequestAsHoldRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    checkOutFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    checkOutFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, jessica, getZonedDateTime().minusHours(5));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, getZonedDateTime().minusHours(4), RequestType.RECALL.getValue());

    IndividualResource requestByCharlotte = requestsFixture.placeItemLevelHoldShelfRequest(
      smallAngryPlanet, charlotte, getZonedDateTime().minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, getZonedDateTime().minusHours(1), RequestType.RECALL.getValue());

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    // move rebecca's recall request from smallAngryPlanet to interestingTimes
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
      requestByRebecca.getId(),
      interestingTimes.getId(),
      RequestType.HOLD.getValue()
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    assertThat("Move request should have correct type",
      moveRequest.getJson().getString("requestType"), is(RequestType.HOLD.getValue()));

    // check positioning on smallAngryPlanet
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(2));
    assertThat(requestBySteve.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(3));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByCharlotte);

    // check positioning on interestingTimes
    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestByRebecca = requestsClient.get(requestByRebecca);
    assertThat(requestByRebecca.getJson().getString(REQUEST_TYPE), is(RequestType.HOLD.getValue()));
    assertThat(requestByRebecca.getJson().getInteger("position"), is(2));
    assertThat(requestByRebecca.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByRebecca);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(3));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(2));
  }

  //This scenerio utilizes two items of the same instance, but the logic in question applies as well for two separate instances.
  @Test
  void cannotDisplacePagedRequest() {
    val secondFloorEconomics = locationsFixture.secondFloorEconomics();
    val mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();

    val itemCopyA = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("10203040506"));

    val itemCopyB = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("90806050402"));

    val james = usersFixture.james();
    val steve = usersFixture.steve();
    val jessica = usersFixture.jessica();

    // James Checks out Item Copy A
    checkOutFixture.checkOutByBarcode(itemCopyA, james, getZonedDateTime());

    // Steve requests Item Copy A
    IndividualResource stevesRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyA, steve, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    assertThat(stevesRequest.getJson().getInteger("position"), is(1));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyA.getBarcode()));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(stevesRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    // Jessica requests Item Copy B
    IndividualResource jessicasRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyB, jessica, getZonedDateTime().minusHours(1), RequestType.PAGE.getValue());

    // Confirm Jessica's request is first on Item Copy B and is a paged request
    assertThat(jessicasRequest.getJson().getInteger("position"), is(1));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyB.getBarcode()));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(jessicasRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    // Move recallRequestForItemCopyA to Item Copy B
    requestsFixture.move(new MoveRequestBuilder(stevesRequest.getId(), itemCopyB.getId()));

    // Confirm Jessica's request is first on Item Copy B and is a paged request
    jessicasRequest = requestsClient.get(jessicasRequest);
    assertThat(jessicasRequest.getJson().getInteger("position"), is(1));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyB.getBarcode()));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));

    // Confirm Steves's request is second on Item Copy B and (is not a paged request (?))
    stevesRequest = requestsClient.get(stevesRequest);
    assertThat(stevesRequest.getJson().getInteger("position"), is(2));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyB.getBarcode()));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));

  }

  private void retainsStoredSummaries(IndividualResource request) {
    assertThat("Updated request in queue should retain stored item summary",
      request.getJson().containsKey("item"), is(true));

    assertThat("Updated request in queue should retain stored requester summary",
      request.getJson().containsKey("requester"), is(true));
  }

  @Test
  void checkoutItemStatusDoesNotChangeOnPagedRequest() {
    val secondFloorEconomics = locationsFixture.secondFloorEconomics();
    val mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();

    val itemCopyA = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("10203040506"));

    val itemCopyB = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("90806050402"));

    assertThat(itemCopyA.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));
    assertThat(itemCopyB.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));

    IndividualResource james = usersFixture.james(); //cate
    IndividualResource steve = usersFixture.steve(); //walker
    IndividualResource jessica = usersFixture.jessica(); //McKenzie

    checkOutFixture.checkOutByBarcode(itemCopyA, james, getZonedDateTime());

    assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

    // Steve requests Item Copy B
    IndividualResource stevesRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyB, steve, getZonedDateTime().minusHours(2), RequestType.PAGE.getValue());

    assertThat(itemsClient.get(itemCopyB).getJson().getJsonObject("status").getString("name"), is(ItemStatus.PAGED.getValue()));

    // Jessica requests Item Copy A
    IndividualResource jessicasRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyA, jessica, getZonedDateTime().minusHours(2), RequestType.RECALL.getValue());

    requestsFixture.move(new MoveRequestBuilder(stevesRequest.getId(),
      itemCopyA.getId(), RequestType.RECALL.getValue()));

    stevesRequest = requestsClient.get(stevesRequest);
    assertThat(stevesRequest.getJson().getInteger("position"), is(2));
    assertThat(itemsClient.get(itemCopyB).getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));

    requestsFixture.move(new MoveRequestBuilder(jessicasRequest.getId(), itemCopyB.getId()));

    // Ensure that itemCopyA is still CHECKED_OUT
    assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));
  }

  /**
   * Use case:<br/>
   * 1. Check out item X to a User A.
   * 2. Check out item Y to a User B.
   * 3. Two loans have been created for the items.
   * 4. Create Recall request to item X for User C.
   * 5. Move the Recall request to item Y.
   * <br/>
   * Expect: The due date for User B's loan is updated after the move and
   *         the due date for User A's loan is not updated after the move.
   *         Both loans have their due dates truncated by the application of
   *         the recall, but this happens only once, at step 4 for User A's
   *         loan and at step 5 for User B's loan. Since the truncation uses
   *         the system date, the due dates will not be equivalent in any
   *         practical application of this scenario, so the test reflects that.
   *
   */
  @Test
  void canUpdateSourceAndDestinationLoanDueDateOnMoveRecallRequest() {
    final ZonedDateTime now = getZonedDateTime();

    // Recall placed 2 hours from now
    final Instant expectedJamesLoanDueDate = now.plusHours(2).toInstant();

    // Move placed 4 hours from now
    final Instant expectedJessicaLoanDueDate = now.plusHours(4).toInstant();

    val smallAngryPlanetItem = itemsFixture.basedUponSmallAngryPlanet();
    val interestingTimesItem = itemsFixture.basedUponInterestingTimes();

    val jamesUser = usersFixture.james();
    val jessicaUser = usersFixture.jessica();
    val steveUser = usersFixture.steve();

    // James and Jessica check out items, so loans will be get created
    checkOutFixture.checkOutByBarcode(smallAngryPlanetItem, jamesUser);
    checkOutFixture.checkOutByBarcode(interestingTimesItem, jessicaUser);

    // Have to mock system clocks to demonstrate a delay between the requests
    // So the dueDates will be recalculated
    freezeTime(expectedJamesLoanDueDate);

    // Create recall request for 'smallAngryPlanet' item to Steve
    IndividualResource recallRequestBySteve = requestsFixture.place(new RequestBuilder()
      .recall()
      .fulfilToHoldShelf()
      .withItemId(smallAngryPlanetItem.getId())
      .withInstanceId(smallAngryPlanetItem.getInstanceId())
      .withRequesterId(steveUser.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    // Have to mock system clocks to demonstrate a delay between the requests
    // So the dueDates will be recalculated
    freezeTime(expectedJessicaLoanDueDate);
    // Then move the 'smallAngryPlanet' recall request to the 'interestingTimes' item
    requestsFixture.move(new MoveRequestBuilder(recallRequestBySteve.getId(),
      interestingTimesItem.getId(), RequestType.RECALL.getValue()));

    // EXPECT:
    // Loans for 1st and 2nd item has expected dueDate.
    List<JsonObject> allItemLoans = loansClient.getAll();
    assertThat(allItemLoans, hasSize(2));

    JsonObject smallAngryPlanetLoan = allItemLoans.stream()
      .filter(loan -> loan.getString("itemId")
        .equals(smallAngryPlanetItem.getId().toString()))
      .findFirst().orElse(new JsonObject());
    JsonObject interestingTimesLoan = allItemLoans.stream()
      .filter(loan -> loan.getString("itemId")
        .equals(interestingTimesItem.getId().toString()))
      .findFirst().orElse(new JsonObject());

    assertThat(smallAngryPlanetLoan.getString("dueDate"), isEquivalentTo(expectedJamesLoanDueDate));
    assertThat(interestingTimesLoan.getString("dueDate"), isEquivalentTo(expectedJessicaLoanDueDate));
  }

  @Test
  void changedDueDateAfterRecallingAnItemShouldRespectTenantTimezone() {
    final String stockholmTimeZone = "Europe/Stockholm";

    val sourceItem = itemsFixture.basedUponSmallAngryPlanet("65654345643");
    val destinationItem = itemsFixture.basedUponSmallAngryPlanet("75605832024");

    val requestServicePoint = servicePointsFixture.cd1();
    val steve = usersFixture.steve();
    val jessica = usersFixture.jessica();

    configClient.create(timezoneConfigurationFor(stockholmTimeZone));

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
      .withName("Can Circulate Rolling With Recalls")
      .withDescription("Can circulate item With Recalls")
      .rolling(Period.days(14))
      .unlimitedRenewals()
      .renewFromSystemDate()
      .withRecallsMinimumGuaranteedLoanPeriod(Period.days(5))
      .withClosedLibraryDueDateManagement(KEEP_THE_CURRENT_DUE_DATE.getValue());

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useFallbackPolicies(loanPolicy.getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.inactiveNotice().getId(),
      overdueFinePoliciesFixture.facultyStandard().getId(),
      lostItemFeePoliciesFixture.facultyStandard().getId());

    setClock(fixed(Instant.parse("2021-02-15T11:24:45Z"), ZoneId.of("UTC")));

    checkOutFixture.checkOutByBarcode(sourceItem, steve);

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(destinationItem, steve);

    //3 days later
    setClock(offset(getClock(), ofDays(3)));

    final IndividualResource recallRequest = requestsFixture.place(
      new RequestBuilder()
        .recall()
        .forItem(sourceItem)
        .by(jessica)
        .withRequestDate(getZonedDateTime())
        .withPickupServicePoint(requestServicePoint));

    requestsFixture.move(new MoveRequestBuilder(recallRequest.getId(),
      destinationItem.getId()));

    final var storedLoan = loansFixture.getLoanById(loan.getId()).getJson();

    assertThat("due date should be end of the day, 5 days from loan date",
      storedLoan.getString("dueDate"), isEquivalentTo(
        ZonedDateTime.parse("2021-02-20T23:59:59+01:00")));
  }

  @Test
  void dueDateChangedEventIsPublished() {
    val secondFloorEconomics = locationsFixture.secondFloorEconomics();
    val mezzanineDisplayCase = locationsFixture.mezzanineDisplayCase();

    val itemCopyA = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(secondFloorEconomics)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("10203040506"));

    val itemCopyB = itemsFixture.basedUponTemeraire(
      holdingBuilder -> holdingBuilder
        .withPermanentLocation(mezzanineDisplayCase)
        .withNoTemporaryLocation(),
      itemBuilder -> itemBuilder
        .withNoPermanentLocation()
        .withNoTemporaryLocation()
        .withBarcode("90806050402"));

    val james = usersFixture.james();
    val jessica = usersFixture.jessica();
    val steve = usersFixture.steve();
    val charlotte = usersFixture.charlotte();

    IndividualResource itemCopyALoan = checkOutFixture.checkOutByBarcode(itemCopyA, james,
      getZonedDateTime());

    requestsFixture.placeHoldShelfRequest(
      itemCopyB, jessica, getZonedDateTime().minusHours(3),
      RequestType.PAGE.getValue());

    IndividualResource recallRequestForItemCopyB = requestsFixture.placeHoldShelfRequest(
      itemCopyB, steve, getZonedDateTime().minusHours(2),
      RequestType.RECALL.getValue());

    requestsFixture.placeHoldShelfRequest(
      itemCopyA, charlotte, getZonedDateTime().minusHours(1),
      RequestType.HOLD.getValue());

    requestsFixture.move(new MoveRequestBuilder(
      recallRequestForItemCopyB.getId(),
      itemCopyA.getId()));

    itemCopyALoan = loansClient.get(itemCopyALoan);

    // There should be four events published - for "check out", for "log event", for "hold" and for "move"
    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(11));

    Map<String, List<JsonObject>> events = publishedEvents.stream().collect(groupingBy(o -> o.getString("eventType")));

    Map<String, List<JsonObject>> logEvents = events.get(LOG_RECORD.name()).stream()
      .collect(groupingBy(e -> new JsonObject(e.getString("eventPayload")).getString("logEventType")));

    Request originalCreatedFromEventPayload = Request.from(new JsonObject(logEvents.get(REQUEST_MOVED.value()).get(0).getString("eventPayload")).getJsonObject("payload").getJsonObject("requests").getJsonObject("original"));
    Request updatedCreatedFromEventPayload = Request.from(new JsonObject(logEvents.get(REQUEST_MOVED.value()).get(0).getString("eventPayload")).getJsonObject("payload").getJsonObject("requests").getJsonObject("updated"));
    assertThat(originalCreatedFromEventPayload.asJson(), Matchers.not(equalTo(updatedCreatedFromEventPayload.asJson())));

    assertThat(originalCreatedFromEventPayload.getItemId(), not(equalTo(updatedCreatedFromEventPayload.getItemId())));
    assertThat(updatedCreatedFromEventPayload.getItemId(), equalTo(itemCopyA.getId().toString()));

    assertThat(events.get(LOAN_DUE_DATE_CHANGED.name()).get(1), isValidLoanDueDateChangedEvent(itemCopyALoan.getJson()));
  }

  private void freezeTime(Instant dateTime) {
    setClock(fixed(dateTime, ZoneOffset.UTC));
  }

  private void requestHasCallNumberStringProperties(JsonObject request, String prefix) {
    JsonObject item = request.getJsonObject("item");

    assertTrue(item.containsKey(CALL_NUMBER_COMPONENTS));
    JsonObject callNumberComponents = item.getJsonObject(CALL_NUMBER_COMPONENTS);

    assertThat(callNumberComponents.getString("callNumber"), is(prefix + "itCn"));
    assertThat(callNumberComponents.getString("prefix"), is(prefix + "itCnPrefix"));
    assertThat(callNumberComponents.getString("suffix"), is(prefix + "itCnSuffix"));

    assertThat(item.getString("enumeration"), is(prefix + "enumeration1"));
    assertThat(item.getString("chronology"), is(prefix + "chronology"));
    assertThat(item.getString("volume"), is(prefix + "vol.1"));
  }
}
