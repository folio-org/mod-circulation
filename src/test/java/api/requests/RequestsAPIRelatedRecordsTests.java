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

public class RequestsAPIRelatedRecordsTests extends APITests {
  private static final String TWO_COPY_NUMBER = "2";

  @Test
  void holdingIdAndInstanceIdIncludedWhenHoldingAndInstanceAreAvailable() {

    final ItemResource smallAngryPlanet = itemsFixture.basedUponSmallAngryPlanet();
    final UUID pickupServicePointId = servicePointsFixture.cd1().getId();

    checkOutFixture.checkOutByBarcode(smallAngryPlanet);

    IndividualResource response = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(usersFixture.charlotte()));

    JsonObject createdRequest = response.getJson();

    assertThat("has holdings record ID",
      createdRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      createdRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      createdRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      createdRequest.getJsonObject("item").getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    Response fetchedRequestResponse = requestsClient.getById(response.getId());

    assertThat(fetchedRequestResponse.getStatusCode(), is(200));

    JsonObject fetchedRequest = fetchedRequestResponse.getJson();

    assertThat("has holdings record ID",
      fetchedRequest.getJsonObject("item").containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      fetchedRequest.getJsonObject("item").getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      fetchedRequest.getJsonObject("item").containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      fetchedRequest.getJsonObject("item").getString("instanceId"),
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

    UUID firstRequestId = requestsClient.create(new RequestBuilder()
      .forItem(smallAngryPlanet)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte))
      .getId();

    UUID secondRequestId = requestsClient.create(new RequestBuilder()
      .forItem(temeraire)
      .withPickupServicePointId(pickupServicePointId)
      .by(charlotte))
      .getId();

    List<JsonObject> fetchedRequestsResponse = requestsClient.getAll();

    JsonObject firstItem = getRecordById(fetchedRequestsResponse, firstRequestId).
      get()
      .getJsonObject("item");

    assertThat("has holdings record ID",
      firstItem.containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      firstItem.getString("holdingsRecordId"),
      is(smallAngryPlanet.getHoldingsRecordId()));

    assertThat("has instance ID",
      firstItem.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      firstItem.getString("instanceId"),
      is(smallAngryPlanet.getInstanceId()));

    assertThat(firstItem.containsKey("copyNumber"), is(true));
    assertThat(firstItem.getString("copyNumber"), is(TWO_COPY_NUMBER));

    JsonObject secondItem = getRecordById(fetchedRequestsResponse, secondRequestId)
      .get()
      .getJsonObject("item");

    assertThat("has holdings record ID",
      secondItem.containsKey("holdingsRecordId"), is(true));

    assertThat("has correct holdings record ID",
      secondItem.getString("holdingsRecordId"),
      is(temeraire.getHoldingsRecordId()));

    assertThat("has instance ID",
      secondItem.containsKey("instanceId"), is(true));

    assertThat("has correct instance ID",
      secondItem.getString("instanceId"),
      is(temeraire.getInstanceId()));
  }
}
