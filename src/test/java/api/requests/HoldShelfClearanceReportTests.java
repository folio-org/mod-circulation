package api.requests;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.circulation.support.StreamToListMapper.toList;
import static org.folio.circulation.support.json.JsonObjectArrayPropertyFetcher.toStream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import api.support.http.ResourceClient;
import api.support.http.UserResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

class HoldShelfClearanceReportTests extends APITests {

  private static final String TOTAL_RECORDS = "totalRecords";
  private static final String REQUESTS_KEY = "requests";
  private static final String STATUS_KEY = "status";
  private static final String REQUESTER_KEY = "requester";
  private static final String ITEM_KEY = "item";
  private static final String PERSONAL_KEY = "personal";
  private static final String BARCODE_KEY = "barcode";
  private static final String LAST_NAME_KEY = "lastName";
  private static final String FIRST_NAME_KEY = "firstName";
  private static final String CLOSED_DATE_KEY = "awaitingPickupRequestClosedDate";
  private static final String CALL_NUMBER_KEY = "callNumber";

  @Test
  void reportIsEmptyWhenThereAreNoRequests() {

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  void openUnfulfilledRequestNotIncludedInReport() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  void requestsAwaitingPickupAreNotIncludedInReport() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));
    checkInFixture.checkInByBarcode(temeraire);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));
    checkInFixture.checkInByBarcode(smallAngryPlanet);

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  void multipleClosedPickupExpiredRequest() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());

    val temeraire = itemsFixture
      .basedUponTemeraire(itemsFixture.addCallNumberStringComponents("tem"));

    val rebecca = usersFixture.rebecca();
    val steve = usersFixture.steve();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilderOnTemeraire = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(steve);
    IndividualResource requestOnTemeraire = requestsClient.create(requestBuilderOnTemeraire);
    checkInFixture.checkInByBarcode(temeraire);
    requestsClient.replace(requestOnTemeraire.getId(),
      requestBuilderOnTemeraire.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000")
    );

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnSmallAngryPlanet = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource requestOnSmallAngryPlanet = requestsClient.create(requestBuilderOnSmallAngryPlanet);
    checkInFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(requestOnSmallAngryPlanet.getId(),
      requestBuilderOnSmallAngryPlanet
        .withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    val requests = toList(toStream(response.getJson(),"requests"));

    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(2));
    assertThat(requests.size(), is(2));

    JsonObject smallAngryPlanetRequest = findRequestByItemId(requests, smallAngryPlanet.getId());
    JsonObject temeraireRequest = findRequestByItemId(requests, temeraire.getId());

    verifyRequest(smallAngryPlanet, rebecca, smallAngryPlanetRequest, RequestStatus.CLOSED_PICKUP_EXPIRED);
    verifyRequest(temeraire, steve, temeraireRequest, RequestStatus.CLOSED_CANCELLED);
  }

  @Test
  void testClosedCancelledExpiredRequest() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());
    val temeraire = itemsFixture.basedUponTemeraire();
    val rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilderOnTemeraire = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve());
    IndividualResource requestOnTemeraire = requestsClient.create(requestBuilderOnTemeraire);
    checkInFixture.checkInByBarcode(temeraire);
    requestsClient.replace(requestOnTemeraire.getId(),
      requestBuilderOnTemeraire.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()));

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnSmallAngryPlanet = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource requestOnSmallAngryPlanet = requestsClient.create(requestBuilderOnSmallAngryPlanet);
    checkInFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(requestOnSmallAngryPlanet.getId(),
      requestBuilderOnSmallAngryPlanet.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_CANCELLED);
  }

  @Test
  void testClosedPickupExpiredRequest() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());
    val temeraire = itemsFixture.basedUponTemeraire();
    val rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));
    checkInFixture.checkInByBarcode(temeraire);

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .withPatronComments("Comment for second request")
      .by(rebecca);
    IndividualResource request = requestsClient.create(requestBuilderOnItem);
    checkInFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(request.getId(),
      requestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-03-11T15:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
    assertThat(response.getJson().getJsonArray(REQUESTS_KEY).getJsonObject(0),
      hasJsonPath("patronComments", "Comment for second request"));
  }

  @Test
  void checkThatResponseGetsRequestWithEarlierClosedDate() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());
    val rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final String earlierAwaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";
    final String laterAwaitingPickupRequestClosedDate = "2018-03-11T10:45:00.000+0000";

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

    // first request
    RequestBuilder firstRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource firstRequest = requestsClient.create(firstRequestBuilderOnItem);

    // second request
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.steve());
    IndividualResource secondRequest = requestsClient.create(secondRequestBuilderOnItem);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    // change "awaitingPickupRequestClosedDate" for the request to the same item
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, earlierAwaitingPickupRequestClosedDate));
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, laterAwaitingPickupRequestClosedDate));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  void checkWhenPickupRequestClosedDateIsEmptyForExpiredRequest() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());
    val rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final String awaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";
    final String emptyRequestClosedDate = "";

    checkOutFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

    // first request
    RequestBuilder firstRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource firstRequest = requestsClient.create(firstRequestBuilderOnItem);

    // second request
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.steve());
    IndividualResource secondRequest = requestsClient.create(secondRequestBuilderOnItem);

    checkInFixture.checkInByBarcode(smallAngryPlanet);

    // change "awaitingPickupRequestClosedDate" for the request to the same item
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, awaitingPickupRequestClosedDate));
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, emptyRequestClosedDate));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  void itemIsCheckedOutAndRequestHasBeenChanged() {

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilder = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve());
    IndividualResource request = requestsClient.create(requestBuilder);
    requestsClient.replace(request.getId(), requestBuilder.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()));

    Response response = ResourceClient.forRequestReport().getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  void checkWhenPickupRequestsExpiredInDifferentServicePoints() {
    val smallAngryPlanet = itemsFixture
      .basedUponSmallAngryPlanet(itemsFixture.addCallNumberStringComponents());

    // init for SP1
    val rebecca = usersFixture.rebecca();
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final String firstAwaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";

    // init for SP2
    val steve = usersFixture.steve();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();
    final String secondAwaitingPickupRequestClosedDate = "2019-03-11T15:55:23.000+0000";

    // #1 create first request in SP1
    RequestBuilder firstRequestBuilderOnItem = new RequestBuilder()
      .open()
      .page()
      .withPickupServicePointId(firstServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource firstRequest = requestsClient.create(firstRequestBuilderOnItem);

    // #2 create second request in SP2
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(secondServicePointId)
      .forItem(smallAngryPlanet)
      .by(steve);
    IndividualResource secondRequest = requestsClient.create(secondRequestBuilderOnItem);

    // #3 check-in item in SP1
    checkInFixture.checkInByBarcode(smallAngryPlanet);

    // #4 expire request1 in SP1
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, firstAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1 >>> not empty
    Response response = ResourceClient.forRequestReport().getById(firstServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);

    // #6 get hold shelf expiration report report in SP2 >>> empty
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 Check that hold shelf expiration report doesn't contain data when the item has the status `Awaiting pickup`,
    // first request - CLOSED_PICKUP_EXPIRED and second request - `Awaiting pickup`
    response = ResourceClient.forRequestReport().getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 expire request in SP2 >> last closed request
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, secondAwaitingPickupRequestClosedDate));

    // #10 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport().getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #11 get hold shelf expiration report in SP2
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    verifyResponse(smallAngryPlanet, steve, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  void checkWhenPickupRequestsCancelledInDifferentServicePoints() {
    val smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    val nod = itemsFixture.basedUponNod();

    // init for SP1
    val rebecca = usersFixture.rebecca();
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final String firstAwaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";

    // init for SP2
    final IndividualResource steve = usersFixture.steve();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();

    // #1 create the first request in SP1
    RequestBuilder firstRequestBuilderOnItem = new RequestBuilder()
      .open()
      .page()
      .withPickupServicePointId(firstServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource firstRequest = requestsClient.create(firstRequestBuilderOnItem);

    // #2 create the second request in SP2
    RequestBuilder secondRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(secondServicePointId)
      .forItem(smallAngryPlanet)
      .by(steve);
    requestsClient.create(secondRequestBuilderOnItem);

    // #3 check-in item in SP1
    checkInFixture.checkInByBarcode(smallAngryPlanet);

    // #4 cancel request1 in SP1
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, firstAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1
    Response response = ResourceClient.forRequestReport().getById(firstServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_CANCELLED);

    // #6 get hold shelf expiration in SP2 >>> empty
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport().getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 get report in SP2 >>> empty
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    final String thirdAwaitingPickupRequestClosedDate = "2019-03-12T15:45:23.000+0000";

    // #1 create the first request in SP1
    RequestBuilder thirdRequestBuilderOnItem = new RequestBuilder()
      .open()
      .page()
      .withPickupServicePointId(firstServicePointId)
      .forItem(nod)
      .by(rebecca);
    IndividualResource thirdRequest = requestsClient.create(thirdRequestBuilderOnItem);

    // #2 create the second request in SP2
    RequestBuilder fourthRequestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(secondServicePointId)
      .forItem(nod)
      .by(steve);
    requestsClient.create(fourthRequestBuilderOnItem);

    // #3 check-in item in SP1
    checkInFixture.checkInByBarcode(nod);

    // #4 cancel request1 in SP1
    requestsClient.replace(thirdRequest.getId(),
      thirdRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, thirdAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1
    response = ResourceClient.forRequestReport().getById(firstServicePointId);
    verifyResponse(nod, rebecca, response, RequestStatus.CLOSED_CANCELLED);

    // #6 get hold shelf expiration in SP2 >>> empty
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport().getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 get report in SP2 >>> empty
    response = ResourceClient.forRequestReport().getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));
  }

  private void verifyResponse(ItemResource item, UserResource requester,
    Response response, RequestStatus status) {

    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(1));

    JsonObject requestJson = response.getJson()
      .getJsonArray(REQUESTS_KEY)
      .getJsonObject(0);

    verifyRequest(item, requester, requestJson, status);
  }

  private void verifyRequest(ItemResource item, UserResource requester,
    JsonObject requestJson, RequestStatus status) {

    assertThat(requestJson.getString(STATUS_KEY), is(status.getValue()));

    JsonObject userJson = requestJson.getJsonObject(REQUESTER_KEY);
    JsonObject requesterPersonalInfo = requester.getJson().getJsonObject(PERSONAL_KEY);
    assertThat(userJson.getString(BARCODE_KEY), is(requester.getBarcode()));
    assertThat(userJson.getString(LAST_NAME_KEY),
      is(requesterPersonalInfo.getString(LAST_NAME_KEY)));
    assertThat(userJson.getString(FIRST_NAME_KEY),
      is(requesterPersonalInfo.getString(FIRST_NAME_KEY)));

    JsonObject itemJson = requestJson.getJsonObject(ITEM_KEY);
    assertThat(itemJson.getString(BARCODE_KEY), is(item.getBarcode()));

    JsonObject expectedCallNumberComponents = item.getJson()
      .getJsonObject("effectiveCallNumberComponents");
    assertThat(itemJson.getString(CALL_NUMBER_KEY),
      is(expectedCallNumberComponents.getString("callNumber")));

    JsonObject actualCallNumberComponents = itemJson
      .getJsonObject("callNumberComponents");

    assertThat(expectedCallNumberComponents.getString("callNumber"),
      is(actualCallNumberComponents.getString("callNumber")));
    assertThat(expectedCallNumberComponents.getString("suffix"),
      is(actualCallNumberComponents.getString("suffix")));
    assertThat(expectedCallNumberComponents.getString("prefix"),
      is(actualCallNumberComponents.getString("prefix")));

    assertThat(itemJson.getString("volume"),
      is(item.getJson().getString("volume")));
    assertThat(itemJson.getString("chronology"),
      is(item.getJson().getString("chronology")));
    assertThat(itemJson.getString("enumeration"),
      is(item.getJson().getString("enumeration")));
  }

  private JsonObject findRequestByItemId(List<JsonObject> requests, UUID itemId) {
    return requests.stream()
      .filter(req -> req.getString("itemId").equals(itemId.toString()))
      .findFirst()
      .orElseThrow(() -> new AssertionError("Can not find Request for item: " + itemId));
  }
}
