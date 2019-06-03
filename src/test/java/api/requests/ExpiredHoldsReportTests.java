package api.requests;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.http.ResourceClient;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.RequestStatus;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ExpiredHoldsReportTests extends APITests {

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
    assertThat(responseJson.getInteger("totalRecords"), is(0));
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
    assertThat(responseJson.getInteger("totalRecords"), is(0));
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
    assertThat(responseJson.getInteger("totalRecords"), is(0));
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
        .put("awaitingPickupRequestClosedDate", "2018-02-11T14:45:23.000+0000")
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
        .put("awaitingPickupRequestClosedDate", "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger("totalRecords"), is(2));
  }

  @Test
  public void oneClosedPickupExpiredRequest()
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
        .put("awaitingPickupRequestClosedDate", "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger("totalRecords"), is(1));
  }

  @Test
  public void closedPickupExpiredRequest()
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
        .put("awaitingPickupRequestClosedDate", "2018-02-11T14:45:23.000+0000"));

    Response response = ResourceClient.forRequestReport(client).getById(pickupServicePointId);
    assertThat(response.getStatusCode(), is(HTTP_OK));

    JsonObject responseJson = response.getJson();
    assertThat(responseJson.getInteger("totalRecords"), is(1));

    JsonObject requestJson = responseJson.getJsonArray("requests").getJsonObject(0);
    assertThat(requestJson.getString("requesterBarcode"), is(rebecca.getBarcode()));
    assertThat(requestJson.getString("itemBarcode"), is(smallAngryPlanet.getBarcode()));
    assertThat(requestJson.getString("requestStatus"), is(RequestStatus.CLOSED_PICKUP_EXPIRED.getValue()));
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
    assertThat(responseJson.getInteger("totalRecords"), is(0));
  }
}
