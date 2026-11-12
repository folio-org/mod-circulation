package api.loans;

import static api.support.APITestContext.getUserId;
import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.http.Limit.noLimit;
import static api.support.http.Offset.noOffset;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsBeforeNow;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.CqlQuery;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class InHouseUseCheckInTest extends APITests {

  @Test
  void isInHouseUseWhenCheckInServicePointIsPrimaryForHomeLocation() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(true));
    verifyLastCheckInWasRecorded(nod.getId(), checkInServicePointId);
  }

  @Test
  void explicitInHouseUseTrueIsStoredWithoutConsultingInference() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();
    final UUID itemServicePointId = servicePointsFixture.cd2().getId();

    final IndividualResource itemLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(itemServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(itemLocation.getId()));

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.james()));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId)
        .withInHouseUse(true));

    assertThat(checkInResponse.getInHouseUse(), is(true));
    assertThat(getLastCheckInRecord(nod.getId()).getBoolean("inHouseUse"), is(true));
  }

  @Test
  void explicitInHouseUseFalseFallsBackToPositiveInference() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(checkInServicePointId)
        .withInHouseUse(false));

    assertThat(checkInResponse.getInHouseUse(), is(true));
    verifyLastCheckInWasRecorded(nod.getId(), checkInServicePointId);
  }

  @Test
  void isInHouseUseWhenItemHasClosedRequests() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.james()));

    IndividualResource recallRequest = requestsFixture.place(new RequestBuilder()
      .recall()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.charlotte()));

    requestsFixture.cancelRequest(recallRequest);

    // Fulfill the page request
    checkOutFixture.checkOutByBarcode(nod, usersFixture.james());
    checkInFixture.checkInByBarcode(nod, checkInServicePointId);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(true));
    verifyLastCheckInWasRecorded(nod.getId(), checkInServicePointId);

    MultipleJsonRecords requestsForItem = requestsFixture.getRequests(
      queryFromTemplate("itemId=%s", nod.getId()), noLimit(), noOffset());
    assertThat(requestsForItem.totalRecords(), is(2));
  }

  @Test
  void isNotInHouseUseWhenItemIsRequested() {
    final UUID checkInServicePointId = servicePointsFixture.cd1().getId();

    final IndividualResource homeLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(checkInServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(homeLocation.getId()));

    requestsFixture.place(new RequestBuilder()
      .page()
      .forItem(nod)
      .withPickupServicePointId(checkInServicePointId)
      .by(usersFixture.james()));
    assertThat(requestsFixture.getQueueFor(nod).getTotalRecords(), is(1));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(false));
    assertThat(getLastCheckInRecord(nod.getId()).getBoolean("inHouseUse"), is(false));
  }

  @Test
  void isNotInHouseUseWhenCheckInServicePointIsNotServingHomeLocation() {
    final UUID itemServicePointId = servicePointsFixture.cd1().getId();
    final UUID checkInServicePointId = servicePointsFixture.cd2().getId();

    final IndividualResource itemLocation = locationsFixture.basedUponExampleLocation(
      builder -> builder.withPrimaryServicePoint(itemServicePointId));

    final IndividualResource nod = itemsFixture.basedUponNod(
      item -> item.withTemporaryLocation(itemLocation.getId()));

    final CheckInByBarcodeResponse checkInResponse = checkInFixture
      .checkInByBarcode(nod, checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
    assertThat(checkInResponse.getJson().containsKey("item"), is(true));
    assertThat(checkInResponse.getInHouseUse(), is(false));
    assertThat(getLastCheckInRecord(nod.getId()).getBoolean("inHouseUse"), is(false));
  }

  @Test
  void checkedOutItemInHouseUseRequiresConfirmation() {
    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource nod = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(nod, usersFixture.james());

    final var response = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointId)
        .withInHouseUse(true));

    assertThat(response.getStatusCode(), is(422));
    final JsonObject error = response.getJson().getJsonArray("errors").getJsonObject(0);
    assertThat(error.getString("code"),
      is("CHECKED_OUT_ITEM_IN_HOUSE_USE_REQUIRES_CONFIRMATION"));
    assertThat(error.getJsonObject("overridableBlock").getString("name"),
      is("checkedOutItemInHouseUseOverride"));
    assertThat(error.getJsonObject("overridableBlock")
      .getJsonArray("missingPermissions").size(), is(0));

    // no side effects
    assertThat(itemsClient.getById(nod.getId()).getJson()
      .getJsonObject("status").getString("name"), is("Checked out"));
    assertThat(checkInOperationClient.getMany(queryFromTemplate("itemId=%s", nod.getId()))
      .totalRecords(), is(0));
  }

  @Test
  void confirmedCheckedOutItemInHouseUseProcessesTwoCheckIns() {
    final UUID servicePointId = servicePointsFixture.cd1().getId();
    final IndividualResource nod = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(nod, usersFixture.james());

    final CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointId)
        .withInHouseUse(true)
        .withCheckedOutItemInHouseUseOverride(true));

    final JsonObject json = response.getJson();
    assertThat(json.containsKey("loan"), is(true));
    assertThat(json.getBoolean("inHouseUse"), is(false));
    assertThat(json.getJsonArray("subsequentCheckIns").size(), is(1));

    final JsonObject second = json.getJsonArray("subsequentCheckIns").getJsonObject(0);
    assertThat(second.getBoolean("inHouseUse"), is(true));
    assertThat(second.containsKey("loan"), is(false));
    assertThat(second.containsKey("subsequentCheckIns"), is(false));

    final var records = checkInOperationClient.getMany(
      queryFromTemplate("itemId=%s", nod.getId()));
    assertThat(records.totalRecords(), is(2));
    assertThat(records.stream().filter(r -> r.getBoolean("inHouseUse")).count(), is(1L));
  }

  @Test
  void overrideWithoutInHouseUseDoesNotTriggerSecondCheckIn() {
    final IndividualResource nod = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(nod, usersFixture.james());

    final CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointsFixture.cd1().getId())
        .withCheckedOutItemInHouseUseOverride(true));

    assertThat(response.getJson().containsKey("subsequentCheckIns"), is(false));
  }

  @Test
  void ordinaryCheckInOmitsSubsequentCheckIns() {
    final IndividualResource nod = itemsFixture.basedUponNod();
    checkOutFixture.checkOutByBarcode(nod, usersFixture.james());

    final CheckInByBarcodeResponse response = checkInFixture.checkInByBarcode(nod);

    assertThat(response.getJson().containsKey("subsequentCheckIns"), is(false));
  }

  private void verifyLastCheckInWasRecorded(UUID itemId, UUID servicePoint) {
    final CqlQuery query = queryFromTemplate("itemId=%s and itemStatusPriorToCheckIn=Available",
      itemId);
    final MultipleJsonRecords recordedOperations = checkInOperationClient.getMany(query);

    assertThat(recordedOperations.totalRecords(), greaterThan(0));

    final String itemEffectiveLocationId = itemsClient.getById(itemId).getJson()
      .getString("effectiveLocationId");

    final JsonObject lastOperation = recordedOperations.getFirst();

    assertThat(lastOperation.getString("occurredDateTime"), withinSecondsBeforeNow(2));
    assertThat(lastOperation.getString("itemId"), is(itemId.toString()));
    assertThat(lastOperation.getString("servicePointId"), is(servicePoint.toString()));
    assertThat(lastOperation.getString("performedByUserId"), is(getUserId()));
    assertThat(lastOperation.getString("itemStatusPriorToCheckIn"), is("Available"));
    assertThat(lastOperation.getString("itemLocationId"), is(itemEffectiveLocationId));
    assertThat(lastOperation.getInteger("requestQueueSize"), is(0));
    assertThat(lastOperation.getBoolean("inHouseUse"), is(true));
    assertTrue(isServedByServicePoint(UUID.fromString(itemEffectiveLocationId), servicePoint));
  }

  private boolean isServedByServicePoint(UUID locationId, UUID servicePointId) {
    final JsonObject location = locationsClient.getById(locationId).getJson();

    return servicePointId.toString().equals(location.getString("primaryServicePoint"));
  }

  private JsonObject getLastCheckInRecord(UUID itemId) {
    final CqlQuery query = queryFromTemplate("itemId=%s", itemId);
    final MultipleJsonRecords recordedOperations = checkInOperationClient.getMany(query);

    assertThat(recordedOperations.totalRecords(), greaterThan(0));

    return recordedOperations.getFirst();
  }
}
