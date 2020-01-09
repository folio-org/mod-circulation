package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.fixtures.ConfigurationExample.timezoneConfigurationFor;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.policy.DueDateManagement.KEEP_THE_CURRENT_DUE_DATE;
import static org.folio.circulation.domain.policy.library.ClosedLibraryStrategyUtils.END_OF_A_DAY;
import static org.folio.circulation.domain.representations.ItemProperties.CALL_NUMBER_COMPONENTS;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

/**
 * @see <a href="https://issues.folio.org/browse/UIREQ-269">UIREQ-269</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-316">CIRC-316</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-333">CIRC-333</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-395">CIRC-395</a>
 */
public class MoveRequestTests extends APITests {

  @After
  public void after() {
    ClockManager.getClockManager().setClock(Clock.systemUTC());
  }

  @Test
  public void canMoveRequestFromOneItemCopyToAnother() {

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

    IndividualResource itemCopyALoan = loansFixture.checkOutByBarcode(itemCopyA, james, DateTime.now(DateTimeZone.UTC));
    assertThat(itemCopyALoan.getJson().getString("userId"), is(james.getId().toString()));

    assertThat(itemCopyALoan.getJson().getString("itemId"), is(itemCopyA.getId().toString()));

    assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

    IndividualResource pageRequestForItemCopyB = requestsFixture.placeHoldShelfRequest(
      itemCopyB, jessica, DateTime.now(DateTimeZone.UTC).minusHours(3), RequestType.PAGE.getValue());

    IndividualResource recallRequestForItemCopyB = requestsFixture.placeHoldShelfRequest(
      itemCopyB, steve, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    IndividualResource holdRequestForItemCopyA = requestsFixture.placeHoldShelfRequest(
      itemCopyA, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.HOLD.getValue());

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
  public void canMoveAShelfHoldRequestToAnAvailableItem() {

    IndividualResource smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents("sap"));
    IndividualResource interestingTimes = itemsFixture
      .basedUponInterestingTimes(itemsFixture.addCallNumberStringComponents("it"));

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC));

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
    retainsStoredSummaries(requestByJessica);
    requestHasCallNumberStringProperties(requestByJessica.getJson(), "it");

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void canMoveARecallRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC).minusHours(5));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(4), RequestType.RECALL.getValue());

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
  public void canMoveTwoRequests() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

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
  public void canMoveAHoldShelfRequestLeavingEmptyQueueAndItemStatusChange() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.place(new RequestBuilder()
      .page()
      .fulfilToHoldShelf()
      .withItemId(smallAngryPlanet.getId())
      .withRequestDate(DateTime.now(DateTimeZone.UTC).minusHours(4))
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
  public void canMoveAHoldShelfRequestToAnEmptyQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC));

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
  public void canMoveAHoldShelfRequestReorderingDestinationRequestQueue() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC).minusHours(4));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC).minusHours(5));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(3));

    // make requests for interestingTimes
    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      interestingTimes, rebecca, DateTime.now(DateTimeZone.UTC).minusHours(5));

    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(1));

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
  public void canMoveAHoldShelfRequestPreventDisplacingOpenRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC).minusHours(4));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC).minusHours(5));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(DateTimeZone.UTC).minusHours(2));

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(1));

    loansFixture.checkInByBarcode(interestingTimes);

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
  public void canMoveARecallRequestAsHoldRequest() {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedinterestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, DateTime.now(DateTimeZone.UTC).minusHours(5));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC).minusHours(4), RequestType.RECALL.getValue());

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(3));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    // make requests for interestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

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
  public void cannotDisplacePagedRequest() {

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
    IndividualResource steve = usersFixture.steve();
    IndividualResource jessica = usersFixture.jessica();

    // James Checks out Item Copy A
    loansFixture.checkOutByBarcode(itemCopyA, james, DateTime.now(DateTimeZone.UTC));

    // Steve requests Item Copy A
    IndividualResource stevesRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyA, steve, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    assertThat(stevesRequest.getJson().getInteger("position"), is(1));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyA.getBarcode()));
    assertThat(stevesRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.CHECKED_OUT.getValue()));
    assertThat(stevesRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    // Jessica requests Item Copy B
    IndividualResource jessicasRequest = requestsFixture.placeHoldShelfRequest(
      itemCopyB, jessica, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.PAGE.getValue());

    // Confirm Jessica's request is first on Item Copy B and is a paged request
    assertThat(jessicasRequest.getJson().getInteger("position"), is(1));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("barcode"), is(itemCopyB.getBarcode()));
    assertThat(jessicasRequest.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(jessicasRequest.getJson().getString("status"), is(RequestStatus.OPEN_NOT_YET_FILLED.getValue()));

    // Move recallRequestForItemCopyA to Item Copy B
    requestsFixture.move(new MoveRequestBuilder(
      stevesRequest.getId(),
      itemCopyB.getId()
    ));

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
  public void checkoutItemStatusDoesNotChangeOnPagedRequest() {

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

        assertThat(itemCopyA.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));
        assertThat(itemCopyB.getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));

        IndividualResource james = usersFixture.james(); //cate
        IndividualResource steve = usersFixture.steve(); //walker
        IndividualResource jessica = usersFixture.jessica(); //McKenzie

        loansFixture.checkOutByBarcode(itemCopyA, james, DateTime.now(DateTimeZone.UTC));

        assertThat(itemsClient.get(itemCopyA).getJson().getJsonObject("status").getString("name"), is(ItemStatus.CHECKED_OUT.getValue()));

        // Steve requests Item Copy B
        IndividualResource stevesRequest = requestsFixture.placeHoldShelfRequest(
          itemCopyB, steve, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.PAGE.getValue());

        assertThat(itemsClient.get(itemCopyB).getJson().getJsonObject("status").getString("name"), is(ItemStatus.PAGED.getValue()));

        // Jessica requests Item Copy A
        IndividualResource jessicasRequest = requestsFixture.placeHoldShelfRequest(
          itemCopyA, jessica, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

        requestsFixture.move(new MoveRequestBuilder(
          stevesRequest.getId(),
          itemCopyA.getId(),
          RequestType.RECALL.getValue()
        ));

        stevesRequest = requestsClient.get(stevesRequest);
        assertThat(stevesRequest.getJson().getInteger("position"), is(2));
        assertThat(itemsClient.get(itemCopyB).getJson().getJsonObject("status").getString("name"), is(ItemStatus.AVAILABLE.getValue()));

        requestsFixture.move(new MoveRequestBuilder(
          jessicasRequest.getId(),
          itemCopyB.getId()
        ));

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
  public void canUpdateSourceAndDestinationLoanDueDateOnMoveRecallRequest() {
    // Recall placed 2 hours from now
    final Instant expectedJamesLoanDueDate = LocalDateTime
        .now().plusHours(2).toInstant(ZoneOffset.UTC);
    // Move placed 4 hours from now
    final Instant expectedJessicaLoanDueDate = LocalDateTime
        .now().plusHours(4).toInstant(ZoneOffset.UTC);

    IndividualResource smallAngryPlanetItem = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimesItem = itemsFixture.basedUponInterestingTimes();

    IndividualResource jamesUser = usersFixture.james();
    IndividualResource jessicaUser = usersFixture.jessica();
    IndividualResource steveUser = usersFixture.steve();

    // James and Jessica check out items, so loans will be get created
    loansFixture.checkOutByBarcode(smallAngryPlanetItem, jamesUser);
    loansFixture.checkOutByBarcode(interestingTimesItem, jessicaUser);

    // Have to mock system clocks to demonstrate a delay between the requests
    // So the dueDates will be recalculated
    freezeTime(expectedJamesLoanDueDate);

    // Create recall request for 'smallAngryPlanet' item to Steve
    IndividualResource recallRequestBySteve = requestsFixture.place(new RequestBuilder()
      .recall()
      .fulfilToHoldShelf()
      .withItemId(smallAngryPlanetItem.getId())
      .withRequesterId(steveUser.getId())
      .withPickupServicePointId(servicePointsFixture.cd1().getId()));

    // Have to mock system clocks to demonstrate a delay between the requests
    // So the dueDates will be recalculated
    freezeTime(expectedJessicaLoanDueDate);
    // Then move the 'smallAngryPlanet' recall request to the 'interestingTimes' item
    requestsFixture.move(new MoveRequestBuilder(recallRequestBySteve.getId(),
      interestingTimesItem.getId(),
      RequestType.RECALL.getValue()
    ));

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
  public void changedDueDateAfterRecallingAnItemShouldRespectTenantTimezone() {

    final String stockholmTimeZone = "Europe/Stockholm";

    final InventoryItemResource sourceItem =
      itemsFixture.basedUponSmallAngryPlanet("65654345643");

    final InventoryItemResource destinationItem =
      itemsFixture.basedUponSmallAngryPlanet("75605832024");

    final IndividualResource requestServicePoint = servicePointsFixture.cd1();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource jessica = usersFixture.jessica();

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

    final DateTime loanDate = DateTime.now(DateTimeZone.UTC).minusDays(3);

    loansFixture.checkOutByBarcode(sourceItem, steve, loanDate);

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      destinationItem, steve, loanDate);

    final String originalDueDate = loan.getJson().getString("dueDate");

    final DateTime requestDate = DateTime.now(DateTimeZone.UTC);

    freezeTime(requestDate);

    final IndividualResource recallRequest = requestsFixture.place(
      new RequestBuilder()
        .recall()
        .forItem(sourceItem)
        .by(jessica)
        .withRequestDate(requestDate)
        .withPickupServicePoint(requestServicePoint));

    requestsFixture.move(new MoveRequestBuilder(recallRequest.getId(),
      destinationItem.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    MatcherAssert.assertThat("due date is the original date",
      storedLoan.getString("dueDate"), not(originalDueDate));

    final DateTime expectedDueDate = loanDate.toLocalDate()
        .toDateTime(END_OF_A_DAY, DateTimeZone.forID(stockholmTimeZone))
        .plusDays(5);

    assertThat("due date should be end of the day, 5 days from loan date",
      storedLoan.getString("dueDate"), isEquivalentTo(expectedDueDate));
  }

  private void freezeTime(DateTime dateTime) {
    freezeTime(Instant.ofEpochMilli(dateTime.getMillis()));
  }

  private void freezeTime(Instant dateTime) {
    ClockManager.getClockManager().setClock(
      Clock.fixed(dateTime, ZoneOffset.UTC));
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
