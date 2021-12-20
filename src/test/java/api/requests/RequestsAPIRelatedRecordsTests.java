package api.requests;

import static api.support.JsonCollectionAssistant.getRecordById;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.RequestBuilder;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

class RequestsAPIRelatedRecordsTests extends APITests {
  private static final String TWO_COPY_NUMBER = "2";

  @Test
  void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withInstanceId(smallAngryPlanet.getInstanceId())
      .by(usersFixture.charlotte()));

    JsonObject createdRequest = response.getJson();

    assertThat("Item has a holdingsRecordId",
      createdRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(false));

    assertThat("has incorrect holdings record ID",
      createdRequest.getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      createdRequest.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      createdRequest.getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    Response fetchedRequestResponse = requestsClient.getById(response.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("Item has a holdingsRecordId",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(false));

    assertThat("has incorrect holdings record ID",
      fetchedRequest.getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedRequest.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequest.getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));
  }

  @Test
  void checkRelatedRecordsForMultipleRequests() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet(
      itemBuilder -> itemBuilder.withCopyNumber(TWO_COPY_NUMBER)
    );

    final ItemResource temeraire = itemsFixture.basedUponTemeraire();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);
    checkOutFixture.checkOutByBarcode(temeraire);

    final IndividualResource charlotte = usersFixture.charlotte();

    UUID instanceId = temeraire.getInstanceId();
    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .withInstanceId(instanceId)
      .by(charlotte))
      .getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .forItem(temeraire)
      .withPickupServicePointId(pickupServicePointId)
      .withInstanceId(instanceId)
      .by(charlotte))
      .getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstItemRecord = getRecordById(fetchedRequestsResponse, firstRequestId).get();
    JsonObject firstItem = firstItemRecord.getJsonObject("item");

    assertThat("has holdings record ID",
      firstItem.containsKey("holdingsRecordId"), is(false));

    assertThat("has correct holdings record ID",
      firstItemRecord.getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedRequestsResponse.get(0).containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequestsResponse.get(0).getString("instanceId"),
      is(instanceId));

    assertThat(firstItem.containsKey("copyNumber"), is(true));
    assertThat(firstItem.getString("copyNumber"), is(TWO_COPY_NUMBER));

    JsonObject secondItemRecord = getRecordById(fetchedRequestsResponse, secondRequestId).get();
    JsonObject secondItem = secondItemRecord.getJsonObject("item");

    assertThat("Item has a holdingsRecordId",
      secondItem.containsKey("holdingsRecordId"), is(false));

    assertThat("has correct holdings record ID",
      secondItemRecord.getString("holdingsRecordId"),
      is(temeraire.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedRequestsResponse.get(1).containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequestsResponse.get(1).getString("instanceId"),
      is(instanceId));
  }
}
