package api.requests;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;

import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class HoldShelfClearanceReportTests extends APITests {

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
  public void reportIsEmptyWhenThereAreNoRequests()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);

    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void openUnfulfilledRequestNotIncludedInReport()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    requestsFixture.place(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void requestsAwaitingPickupAreNotIncludedInReport()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));
    loansFixture.checkInByBarcode(temeraire);

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(usersFixture.rebecca()));
    loansFixture.checkInByBarcode(smallAngryPlanet);

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void multipleClosedPickupExpiredRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilderOnTemeraire = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve());
    IndividualResource requestOnTemeraire = requestsClient.create(requestBuilderOnTemeraire);
    loansFixture.checkInByBarcode(temeraire);
    requestsClient.replace(requestOnTemeraire.getId(),
      requestBuilderOnTemeraire.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000")
    );

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnSmallAngryPlanet = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource requestOnSmallAngryPlanet = requestsClient.create(requestBuilderOnSmallAngryPlanet);
    loansFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(requestOnSmallAngryPlanet.getId(),
      requestBuilderOnSmallAngryPlanet
        .withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(2));
  }

  @Test
  public void testClosedCancelledExpiredRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilderOnTemeraire = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve());
    IndividualResource requestOnTemeraire = requestsClient.create(requestBuilderOnTemeraire);
    loansFixture.checkInByBarcode(temeraire);
    requestsClient.replace(requestOnTemeraire.getId(),
      requestBuilderOnTemeraire.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()));

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnSmallAngryPlanet = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource requestOnSmallAngryPlanet = requestsClient.create(requestBuilderOnSmallAngryPlanet);
    loansFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(requestOnSmallAngryPlanet.getId(),
      requestBuilderOnSmallAngryPlanet.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_CANCELLED);
  }

  @Test
  public void testClosedPickupExpiredRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    requestsClient.create(new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve()));
    loansFixture.checkInByBarcode(temeraire);

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());
    RequestBuilder requestBuilderOnItem = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebecca);
    IndividualResource request = requestsClient.create(requestBuilderOnItem);
    loansFixture.checkInByBarcode(smallAngryPlanet);
    requestsClient.replace(request.getId(),
      requestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, "2018-03-11T15:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  public void checkThatResponseGetsRequestWithEarlierClosedDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final String earlierAwaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";
    final String laterAwaitingPickupRequestClosedDate = "2018-03-11T10:45:00.000+0000";

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

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

    loansFixture.checkInByBarcode(smallAngryPlanet);

    // change "awaitingPickupRequestClosedDate" for the request to the same item
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, earlierAwaitingPickupRequestClosedDate));
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, laterAwaitingPickupRequestClosedDate));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  public void checkWhenPickupRequestClosedDateIsEmptyForExpiredRequest()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final IndividualResource rebecca = usersFixture.rebecca();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();
    final String awaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";
    final String emptyRequestClosedDate = "";

    loansFixture.checkOutByBarcode(smallAngryPlanet, usersFixture.james());

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

    loansFixture.checkInByBarcode(smallAngryPlanet);

    // change "awaitingPickupRequestClosedDate" for the request to the same item
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, awaitingPickupRequestClosedDate));
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, emptyRequestClosedDate));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    verifyResponse(smallAngryPlanet, rebecca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  public void itemIsCheckedOutAndRequestHasBeenChanged()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    loansFixture.checkOutByBarcode(temeraire, usersFixture.charlotte());
    RequestBuilder requestBuilder = new RequestBuilder()
      .open()
      .hold()
      .withPickupServicePointId(pickupServicePointId)
      .forItem(temeraire)
      .by(usersFixture.steve());
    IndividualResource request = requestsClient.create(requestBuilder);
    requestsClient.replace(request.getId(), requestBuilder.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(0));
  }

  @Test
  public void checkWhenPickupRequestsExpiredInDifferentServicePoints()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();

    // init for SP1
    final IndividualResource rebeca = usersFixture.rebecca();
    final UUID firstServicePointId = servicePointsFixture.cd1().getId();
    final String firstAwaitingPickupRequestClosedDate = "2019-03-11T15:45:23.000+0000";

    // init for SP2
    final IndividualResource steve = usersFixture.steve();
    final UUID secondServicePointId = servicePointsFixture.cd2().getId();
    final String secondAwaitingPickupRequestClosedDate = "2019-03-11T15:55:23.000+0000";

    // #1 create first request in SP1
    RequestBuilder firstRequestBuilderOnItem = new RequestBuilder()
      .open()
      .page()
      .withPickupServicePointId(firstServicePointId)
      .forItem(smallAngryPlanet)
      .by(rebeca);
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
    loansFixture.checkInByBarcode(smallAngryPlanet);

    // #4 expire request1 in SP1
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, firstAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1 >>> not empty
    Response response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    verifyResponse(smallAngryPlanet, rebeca, response, RequestStatus.CLOSED_PICKUP_EXPIRED);

    // #6 get hold shelf expiration report report in SP2 >>> empty
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    loansFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 Check that hold shelf expiration report doesn't contain data when the item has the status `Awaiting pickup`,
    // first request - CLOSED_PICKUP_EXPIRED and second request - `Awaiting pickup`
    response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 expire request in SP2 >> last closed request
    requestsClient.replace(secondRequest.getId(),
      secondRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()).create()
        .put(CLOSED_DATE_KEY, secondAwaitingPickupRequestClosedDate));

    // #10 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #11 get hold shelf expiration report in SP2
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    verifyResponse(smallAngryPlanet, steve, response, RequestStatus.CLOSED_PICKUP_EXPIRED);
  }

  @Test
  public void checkWhenPickupRequestsCancelledInDifferentServicePoints()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final InventoryItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final InventoryItemResource nod = itemsFixture.basedUponNod();

    // init for SP1
    final IndividualResource rebeca = usersFixture.rebecca();
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
      .by(rebeca);
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
    loansFixture.checkInByBarcode(smallAngryPlanet);

    // #4 cancel request1 in SP1
    requestsClient.replace(firstRequest.getId(),
      firstRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, firstAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1
    Response response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    verifyResponse(smallAngryPlanet, rebeca, response, RequestStatus.CLOSED_CANCELLED);

    // #6 get hold shelf expiration in SP2 >>> empty
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    loansFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(smallAngryPlanet)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 get report in SP2 >>> empty
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    final String thirdAwaitingPickupRequestClosedDate = "2019-03-12T15:45:23.000+0000";

    // #1 create the first request in SP1
    RequestBuilder thirdRequestBuilderOnItem = new RequestBuilder()
      .open()
      .page()
      .withPickupServicePointId(firstServicePointId)
      .forItem(nod)
      .by(rebeca);
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
    loansFixture.checkInByBarcode(nod);

    // #4 cancel request1 in SP1
    requestsClient.replace(thirdRequest.getId(),
      thirdRequestBuilderOnItem.withStatus(RequestStatus.CLOSED_CANCELLED.getValue()).create()
        .put(CLOSED_DATE_KEY, thirdAwaitingPickupRequestClosedDate));

    // #5 get hold shelf expiration report in SP1
    response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    verifyResponse(nod, rebeca, response, RequestStatus.CLOSED_CANCELLED);

    // #6 get hold shelf expiration in SP2 >>> empty
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #7 check-in item in SP2
    loansFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(nod)
      .on(DateTime.now(DateTimeZone.UTC))
      .at(secondServicePointId));

    // #8 get hold shelf expiration report in SP1 >>> empty
    response = ResourceClient.forRequestReport(client).getById(firstServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));

    // #9 get report in SP2 >>> empty
    response = ResourceClient.forRequestReport(client).getById(secondServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));
    assertThat(response.getJson().getInteger(TOTAL_RECORDS), is(0));
  }

  private void verifyResponse(InventoryItemResource item,
                              IndividualResource requester,
                              Response response,
                              RequestStatus status) {
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger(TOTAL_RECORDS), is(1));

    JsonObject requestJson = responseJson.getJsonArray(REQUESTS_KEY).getJsonObject(0);
    assertThat(requestJson.getString(STATUS_KEY), is(status.getValue()));

    JsonObject userJson = requestJson.getJsonObject(REQUESTER_KEY);
    JsonObject requesterPersonalInfo = requester.getJson().getJsonObject(PERSONAL_KEY);
    assertThat(userJson.getString(BARCODE_KEY), is(requester.getBarcode()));
    assertThat(userJson.getString(LAST_NAME_KEY), is(requesterPersonalInfo.getString(LAST_NAME_KEY)));
    assertThat(userJson.getString(FIRST_NAME_KEY), is(requesterPersonalInfo.getString(FIRST_NAME_KEY)));

    JsonObject itemJson = requestJson.getJsonObject(ITEM_KEY);
    assertThat(itemJson.getString(BARCODE_KEY), is(item.getBarcode()));
    String callNumber = item.getHoldingsRecord().getJson().getString(CALL_NUMBER_KEY);
    assertThat(itemJson.getString(CALL_NUMBER_KEY), is(callNumber));
  }
}
