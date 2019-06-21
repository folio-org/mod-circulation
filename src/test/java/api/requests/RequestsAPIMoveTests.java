package api.requests;

import static org.folio.circulation.domain.representations.RequestProperties.REQUEST_TYPE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.domain.RequestType;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import io.vertx.core.json.JsonObject;
import junitparams.JUnitParamsRunner;

@RunWith(JUnitParamsRunner.class)
public class RequestsAPIMoveTests extends APITests {

  @Test
  public void cannotMoveHoldRequestsWithRequestPolicyNotAllowingHolds()
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

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    loansFixture.checkOutByBarcode(smallAngryPlanet, jessica);
    
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    IndividualResource requestByJames = requestsFixture.placeRecallRequest(
      uponInterestingTimes, james, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    // move james' recall request as a hold shelf request from smallAngryPlanet to uponInterestingTimes
    Response response = requestsFixture.attemptMove(RequestBuilder.from(requestByJames)
      .hold()
      .withDestinationItemId(smallAngryPlanet.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should have correct response status code", response.getStatusCode(), is(422));
    assertThat("Move request should have correct response message",
      response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Hold requests are not allowed for this patron and item combination"));

    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));
    
    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void canMoveAShelfHoldRequestToAnAvailableItem()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
  
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    // move jessica's hold shelf request from smallAngryPlanet to uponInterestingTimes
    IndividualResource moveRequest = requestsFixture.move(RequestBuilder.from(requestByJessica)
      .withDestinationItemId(uponInterestingTimes.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should not retain stored destination item id",
      moveRequest.getJson().containsKey("destinationItemId"), is(false));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    
    assertThat("Move request should have correct type",
        moveRequest.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getString(REQUEST_TYPE), is(RequestType.PAGE.getValue()));
    assertThat(requestByJessica.getJson().getJsonObject("item").getString("status"), is(ItemStatus.PAGED.getValue()));
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));
    
    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void cannotMoveARecallRequestToAnEmptyQueue()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {
  
    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    // make recall requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeRecallRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    // move jessica's recall request from smallAngryPlanet to uponInterestingTimes
    Response response = requestsFixture.attemptMove(RequestBuilder.from(requestByJessica)
      .withDestinationItemId(uponInterestingTimes.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should have correct response status code", response.getStatusCode(), is(422));
    assertThat("Move request should have correct response message",
      response.getJson().getJsonArray("errors").getJsonObject(0).getString("message"),
      is("Recalls can't be moved to checked out items that have not been previously recalled."));

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getString(REQUEST_TYPE), is(RequestType.RECALL.getValue()));
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(smallAngryPlanet.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(1));
    
    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(0));
  }

  @Test
  public void canMoveARecallRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);
    
    // charlotte checks out basedUponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeRecallRequest(
      smallAngryPlanet, steve, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    IndividualResource requestByRebecca = requestsFixture.placeRecallRequest(
      smallAngryPlanet, rebecca, new DateTime(2018, 2, 4, 7, 4, 53, DateTimeZone.UTC));

    // make requests for uponInterestingTimes
    IndividualResource requestByJames = requestsFixture.placeRecallRequest(
      uponInterestingTimes, james, new DateTime(2018, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    // move steve's recall request from smallAngryPlanet to uponInterestingTimes
    IndividualResource moveRequest = requestsFixture.move(RequestBuilder.from(requestBySteve)
      .withDestinationItemId(uponInterestingTimes.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should not retain stored destination item id",
      moveRequest.getJson().containsKey("destinationItemId"), is(false));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));

    // check positioning on uponInterestingTimes
    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestBySteve = requestsClient.get(requestBySteve);
    assertThat(requestBySteve.getJson().getInteger("position"), is(2));
    assertThat(requestBySteve.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestBySteve);

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

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(3));

    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(2));
  }

  @Test
  public void canMoveAHoldShelfRequestToAnEmptyQueue()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource charlotte = usersFixture.charlotte();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);

    // charlotte checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    // move jessica's hold shelf request from smallAngryPlanet to uponInterestingTimes
    IndividualResource moveRequest = requestsFixture.move(RequestBuilder.from(requestByJessica)
      .withDestinationItemId(uponInterestingTimes.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should not retain stored destination item id",
      moveRequest.getJson().containsKey("destinationItemId"), is(false));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(1));
    assertThat(requestByJessica.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(0));
    
    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(1));
  }

  @Test
  public void canMoveAHoldShelfRequest()
    throws InterruptedException,
    ExecutionException,
    TimeoutException,
    MalformedURLException {

    UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    IndividualResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    IndividualResource uponInterestingTimes = itemsFixture.basedUponInterestingTimes();
    
    IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();
    IndividualResource steve = usersFixture.steve();
    IndividualResource charlotte = usersFixture.charlotte();
    IndividualResource rebecca = usersFixture.rebecca();

    // james checks out basedUponSmallAngryPlanet
    loansFixture.checkOutByBarcode(smallAngryPlanet, james);
    
    // charlotte checks out basedUponInterestingTimes
    loansFixture.checkOutByBarcode(uponInterestingTimes, charlotte);

    // make requests for smallAngryPlanet
    IndividualResource requestByJessica = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, jessica, new DateTime(2017, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    IndividualResource requestBySteve = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, steve, new DateTime(2017, 10, 27, 11, 54, 37, DateTimeZone.UTC));

    IndividualResource requestByCharlotte = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, charlotte, new DateTime(2018, 1, 10, 15, 34, 21, DateTimeZone.UTC));

    IndividualResource requestByRebecca = requestsFixture.placeHoldShelfRequest(
      smallAngryPlanet, rebecca, new DateTime(2018, 2, 4, 7, 4, 53, DateTimeZone.UTC));

    // make requests for uponInterestingTimes
    IndividualResource requestByJames = requestsFixture.placeHoldShelfRequest(
      uponInterestingTimes, james, new DateTime(2018, 7, 22, 10, 22, 54, DateTimeZone.UTC));

    // move jessica's hold shelf request from smallAngryPlanet to uponInterestingTimes
    IndividualResource moveRequest = requestsFixture.move(RequestBuilder.from(requestByJessica)
      .withDestinationItemId(uponInterestingTimes.getId())
      .fulfilToHoldShelf(pickupServicePointId));

    assertThat("Move request should not retain stored destination item id",
      moveRequest.getJson().containsKey("destinationItemId"), is(false));

    assertThat("Move request should have correct item id",
      moveRequest.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));

    // check positioning on uponInterestingTimes
    requestByJames = requestsClient.get(requestByJames);
    assertThat(requestByJames.getJson().getInteger("position"), is(1));
    assertThat(requestByJames.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJames);

    requestByJessica = requestsClient.get(requestByJessica);
    assertThat(requestByJessica.getJson().getInteger("position"), is(2));
    assertThat(requestByJessica.getJson().getString("itemId"), is(uponInterestingTimes.getId().toString()));
    retainsStoredSummaries(requestByJessica);

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

    // check item queues are correct size
    MultipleRecords<JsonObject> smallAngryPlanetQueue = requestsFixture.getQueueFor(smallAngryPlanet);
    assertThat(smallAngryPlanetQueue.getTotalRecords(), is(3));

    MultipleRecords<JsonObject> uponInterestingTimesQueue = requestsFixture.getQueueFor(uponInterestingTimes);
    assertThat(uponInterestingTimesQueue.getTotalRecords(), is(2));
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
