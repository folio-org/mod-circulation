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

import org.joda.time.Seconds;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.MultipleJsonRecords;
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
      item -> item
        .withTemporaryLocation(homeLocation.getId()));

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
  }

  private void verifyLastCheckInWasRecorded(UUID itemId, UUID servicePoint) {
    final CqlQuery query = queryFromTemplate("itemId=%s and itemStatusPriorToCheckIn=Available",
      itemId);
    final MultipleJsonRecords recordedOperations = checkInOperationClient.getMany(query);

    assertThat(recordedOperations.totalRecords(), greaterThan(0));

    final String itemEffectiveLocationId = itemsClient.getById(itemId).getJson()
      .getString("effectiveLocationId");

    final JsonObject lastOperation = recordedOperations.getFirst();

    assertThat(lastOperation.getString("occurredDateTime"),
      withinSecondsBeforeNow(Seconds.seconds(2)));
    assertThat(lastOperation.getString("itemId"), is(itemId.toString()));
    assertThat(lastOperation.getString("servicePointId"), is(servicePoint.toString()));
    assertThat(lastOperation.getString("performedByUserId"), is(getUserId()));
    assertThat(lastOperation.getString("itemStatusPriorToCheckIn"), is("Available"));
    assertThat(lastOperation.getString("itemLocationId"), is(itemEffectiveLocationId));
    assertThat(lastOperation.getInteger("requestQueueSize"), is(0));
    assertTrue(isServedByServicePoint(UUID.fromString(itemEffectiveLocationId), servicePoint));
  }

  private boolean isServedByServicePoint(UUID locationId, UUID servicePointId) {
    final JsonObject location = locationsClient.getById(locationId).getJson();

    return servicePointId.toString().equals(location.getString("primaryServicePoint"));
  }
}
