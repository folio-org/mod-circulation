package api.requests.scenarios;

import static api.support.builders.ItemBuilder.AVAILABLE;
import static api.support.builders.ItemBuilder.PAGED;
import static api.support.builders.RequestBuilder.OPEN_AWAITING_PICKUP;
import static api.support.matchers.ItemStatusCodeMatcher.hasItemStatus;
import static java.util.Collections.singletonList;
import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanPolicyBuilder;
import api.support.builders.MoveRequestBuilder;
import api.support.builders.NoticeConfigurationBuilder;
import api.support.builders.NoticePolicyBuilder;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;

/**
 * Notes:<br>
 *  MGD = Minimum guaranteed due date<br>
 *  RD = Recall due date<br>
 *
 * @see <a href="https://issues.folio.org/browse/UIREQ-269">UIREQ-269</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-316">CIRC-316</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-333">CIRC-333</a>
 * @see <a href="https://issues.folio.org/browse/CIRC-395">CIRC-395</a>
 */
public class MoveRequestTests extends APITests {
  private static final String RECALL_TO_LOANEE = "Recall loanee";
  private static Clock clock;

  private NoticePolicyBuilder noticePolicy;

  @BeforeClass
  public static void setUpBeforeClass() {
    clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);
  }

  @Before
  public void setUp() {
    // reset the clock before each test (just in case)
    ClockManager.getClockManager().setClock(clock);
  }

  @Before
  public void setUpNoticePolicy() throws MalformedURLException, InterruptedException, ExecutionException, TimeoutException {
    UUID recallToLoaneeTemplateId = UUID.randomUUID();
    JsonObject recallToLoaneeConfiguration = new NoticeConfigurationBuilder()
      .withTemplateId(recallToLoaneeTemplateId)
      .withEventType(RECALL_TO_LOANEE)
      .create();

    noticePolicy = new NoticePolicyBuilder()
      .withName("Policy with recall notice")
      .withLoanNotices(singletonList(recallToLoaneeConfiguration));

    useLoanPolicyAsFallback(
      loanPoliciesFixture.canCirculateRolling().getId(),
      requestPoliciesFixture.allowAllRequestPolicy().getId(),
      noticePoliciesFixture.create(noticePolicy).getId());
  }

  @Test
  public void cannotMoveRecallRequestsWithRequestPolicyNotAllowingHolds()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final String anyNoticePolicy = noticePoliciesFixture.activeNotice().getId().toString();
    final String anyLoanPolicy = loanPoliciesFixture.canCirculateRolling().getId().toString();
    final String bookMaterialType = materialTypesFixture.book().getId().toString();
    final String anyRequestPolicy = requestPoliciesFixture.allowAllRequestPolicy().getId().toString();

    ArrayList<RequestType> allowedRequestTypes = new ArrayList<>();
    allowedRequestTypes.add(RequestType.RECALL);
    allowedRequestTypes.add(RequestType.PAGE);
    final String noHoldRequestPolicy = requestPoliciesFixture.customRequestPolicy(allowedRequestTypes,
      "All But Hold", "All but Hold request policy").getId().toString();

    //This rule is set up to show that the fallback policy won't be used but the material type rule m is used instead.
    //The material type rule m allows any patron to place any request but HOLDs on any BOOK, loan or notice types
    final String rules = String.join("\n",
      "priority: t, s, c, b, a, m, g",
      "fallback-policy : l " + anyLoanPolicy + " r " + anyRequestPolicy + " n " + anyNoticePolicy + "\n",
      "m " + bookMaterialType + ": l " + anyLoanPolicy + " r " + noHoldRequestPolicy +" n " + anyNoticePolicy
    );

    setRules(rules);

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);

    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(2), RequestType.RECALL.getValue());

    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      interestingTimes, james, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    // move james' recall request as a hold shelf request from smallAngryPlanet to interestingTimes
    Response response = requestsFixture.attemptMove(new MoveRequestBuilder(
      requestByJames.getId(),
      smallAngryPlanet.getId(),
      RequestType.HOLD.getValue()
    ));

    assertThat("Move request should have correct response status code", response.getStatusCode(), is(422));
    assertThat("Move request should have correct response message",
      response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Hold requests are not allowed for this patron and item combination"));

    requestByCharlotte = requestsClient.get(requestByCharlotte);
    assertThat(requestByCharlotte.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByCharlotte.getJson().getInteger("position"), is(1));
    assertThat(requestByCharlotte.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(1));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void canMoveAShelfHoldRequestToAnAvailableItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();

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
      interestingTimes.getId(),
      null
    ));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    assertThat("Move request should have correct type",
        moveRequest.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));
    assertThat(requestByJessica.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
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
  public void canMoveARecallRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
        interestingTimes.getId(),
        null
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
  public void canMoveTwoRequests()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
        interestingTimes.getId(),
        null
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
        interestingTimes.getId(),
        null
    ));

    assertThat("Move request should have correct item id",
        secondMoveRequest.getJson().getString("itemId"), is(interestingTimes.getId().toString()));

    // check positioning after second move
    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(2));
    assertThat(requestBySteve.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestBySteve);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(2));
  }

  @Test
  public void canMoveAHoldShelfRequestLeavingEmptyQueueAndItemStatusChange()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
  public void canMoveAHoldShelfRequestToAnEmptyQueue()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
      interestingTimes.getId(),
      null
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
  public void canMoveAHoldShelfRequestReorderingDestinationRequestQueue()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
      interestingTimes.getId(),
      null
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
    assertThat(requestByJessica.getJson().getInteger("position"), is(2));
    assertThat(requestByJessica.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(3));
    assertThat(requestByJames.getJson().getString("itemId"), is(interestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(2));

    MultipleRecords<JsonObject> interestingTimesQueue = requestsFixture.getQueueFor(interestingTimes);
    assertThat(interestingTimesQueue.getTotalRecords(), is(3));
  }

  @Test
  public void canMoveAHoldShelfRequestPreventDisplacingOpenRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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
      interestingTimes.getId(),
      null
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
  public void canMoveARecallRequestAsHoldRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

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

  @Test
  public void moveRecallRequestWithoutExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out smallAngryPlanet
    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(0));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
        requestByJessica.getId(),
        smallAngryPlanet.getId(),
        RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
        moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
        moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the current date",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
        patronNoticesClient.getAll().size(), is(1));
  }

  @Test
  public void moveRecallRequestWithExistingRecallsAndWithNoPolicyValuesChangesDueDateToSystemDate()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    // steve checks out smallAngryPlanet
    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on smallAngryPlanet
    requestsFixture.placeHoldShelfRequest(
        smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the current date",
        storedLoan.getString("dueDate"), is(expectedDueDate));


    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
        requestByJessica.getId(),
        smallAngryPlanet.getId(),
        RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
        moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
        moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));


    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    List<JsonObject> patronNotices = patronNoticesClient.getAll();

    assertThat("move recall request unexpectedly sent another patron notice",
        patronNotices.size(), is(2));
  }

  @Test
  public void moveRecallRequestWithoutExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.months(2));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePoliciesFixture.create(noticePolicy).getId());

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(0));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
        requestByJessica.getId(),
        smallAngryPlanet.getId(),
        RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
        moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
        moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));


    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (2 months)",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request notice has not been sent",
        patronNoticesClient.getAll().size(), is(1));
  }

  @Test
  public void moveRecallRequestWithExistingRecallsAndWithMGDAndRDValuesChangesDueDateToRD()
      throws InterruptedException,
      ExecutionException,
      TimeoutException,
      MalformedURLException {
    final IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource interestingTimes = itemsFixture.basedUponInterestingTimes();
    final IndividualResource steve = usersFixture.steve();
    final IndividualResource charlotte = usersFixture.charlotte();
    final IndividualResource jessica = usersFixture.jessica();

    final LoanPolicyBuilder canCirculateRollingPolicy = new LoanPolicyBuilder()
        .withName("Can Circulate Rolling With Recalls")
        .withDescription("Can circulate item With Recalls")
        .rolling(Period.weeks(3))
        .unlimitedRenewals()
        .renewFromSystemDate()
        .withRecallsMinimumGuaranteedLoanPeriod(Period.weeks(2))
        .withRecallsRecallReturnInterval(Period.months(2));

    final IndividualResource loanPolicy = loanPoliciesFixture.create(canCirculateRollingPolicy);

    useLoanPolicyAsFallback(loanPolicy.getId(),
        requestPoliciesFixture.allowAllRequestPolicy().getId(),
        noticePoliciesFixture.create(noticePolicy).getId());

    final IndividualResource loan = loansFixture.checkOutByBarcode(
      smallAngryPlanet, steve, DateTime.now(DateTimeZone.UTC));

    final String originalDueDate = loan.getJson().getString("dueDate");

    // charlotte places recall request on smallAngryPlanet
    requestsFixture.placeHoldShelfRequest(
        smallAngryPlanet, charlotte, DateTime.now(DateTimeZone.UTC).minusHours(1), RequestType.RECALL.getValue());

    JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date is the original date",
        storedLoan.getString("dueDate"), not(originalDueDate));

    final String expectedDueDate = ClockManager.getClockManager().getDateTime().plusMonths(2).toString(ISODateTimeFormat.dateTime());
    assertThat("due date is not the recall due date (2 months)",
        storedLoan.getString("dueDate"), is(expectedDueDate));


    // charlotte checks out interestingTimes
    loansFixture.checkOutByBarcode(interestingTimes, charlotte);

    // jessica places recall request on interestingTimes
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
        interestingTimes, jessica, DateTime.now(DateTimeZone.UTC), RequestType.RECALL.getValue());

    assertThat(patronNoticesClient.getAll().size(), is(1));

    // move jessica's recall request from interestingTimes to smallAngryPlanet
    IndividualResource moveRequest = requestsFixture.move(new MoveRequestBuilder(
        requestByJessica.getId(),
        smallAngryPlanet.getId(),
        RequestType.RECALL.getValue()
    ));

    assertThat("Move request should have correct item id",
        moveRequest.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));

    assertThat("Move request should have correct type",
        moveRequest.getJson().getString("requestType"), is(RequestType.RECALL.getValue()));

    storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("due date has changed",
        storedLoan.getString("dueDate"), is(expectedDueDate));

    assertThat("move recall request unexpectedly sent another patron notice",
        patronNoticesClient.getAll().size(), is(2));
  }

  private void retainsStoredSummaries(IndividualResource request) {
    assertThat("Updated request in queue should retain stored item summary",
      request.getJson().containsKey("item"), is(true));

    assertThat("Updated request in queue should retain stored requester summary",
      request.getJson().containsKey("requester"), is(true));
  }

  private void setRules(String rules) {
    try {
      circulationRulesFixture.updateCirculationRules(rules);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
