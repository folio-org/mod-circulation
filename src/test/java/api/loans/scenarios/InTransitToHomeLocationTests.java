package api.loans.scenarios;

import static api.support.fixtures.TemplateContextMatchers.getItemContextMatchers;
import static api.support.fixtures.TemplateContextMatchers.getTransitContextMatchers;
import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.http.InventoryItemResource;
import api.support.matchers.JsonObjectMatcher;
import io.vertx.core.json.JsonObject;

public class InTransitToHomeLocationTests extends APITests {
  @Test
  public void isPlacedInTransitWhenCheckedInToReturnItemAtServicePointNotServingHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final InventoryItemResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource otherServicePoint = servicePointsFixture.cd2();

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not in transit",
      itemRepresentation.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item should have a destination",
      itemRepresentation.getString("inTransitDestinationServicePointId"),
      is(primaryServicePoint.getId()));

    assertThat("in transit item should have a extended destination properties",
      itemRepresentation.containsKey("inTransitDestinationServicePoint"), is(true));

    final JsonObject destinationServicePoint =
      itemRepresentation.getJsonObject("inTransitDestinationServicePoint");

    assertThat("extended destination properties should include id",
      destinationServicePoint.getString("id"), is(primaryServicePoint.getId()));

    assertThat("extended destination properties should include name",
      destinationServicePoint.getString("name"), is("Circ Desk 1"));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    assertThat("in transit item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item").getString("inTransitDestinationServicePointId"),
      is(primaryServicePoint.getId()));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not in transit",
      updatedNod.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item in storage should have a destination",
      updatedNod.getString("inTransitDestinationServicePointId"),
      is(primaryServicePoint.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not in transit",
      storedLoan.getString("itemStatus"), is("In transit"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(otherServicePoint.getId()));

    Map<String, Matcher<String>> staffSlipContextMatchers = new HashMap<>();
    staffSlipContextMatchers.putAll(getItemContextMatchers(nod, true));
    staffSlipContextMatchers.putAll(getTransitContextMatchers(otherServicePoint, primaryServicePoint));

    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();
    assertThat(staffSlipContext, JsonObjectMatcher.allOfPaths(staffSlipContextMatchers));
  }

  @Test
  public void isAvailableWhenCheckedInToReceiveAtPrimaryServicePointForHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource otherServicePoint = servicePointsFixture.cd2();

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServicePoint.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(primaryServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not available",
      itemRepresentation.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should not be present in response",
      loanRepresentation, nullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), is(false));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage should not change",
      storedLoan.getString("itemStatus"), is("In transit"));

    assertThat("Checkin Service Point Id should not change",
      storedLoan.getString("checkinServicePointId"), is(otherServicePoint.getId()));
  }

  @Test
  public void isAvailableWhenCheckedInToReceiveAtNonPrimaryServicePointForHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();
    final IndividualResource otherServingServicePoint = servicePointsFixture.cd2();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .servedBy(otherServingServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource otherServicePoint = servicePointsFixture.cd3();

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServicePoint.getId()));

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServingServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not available",
      itemRepresentation.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should not be present in response",
      loanRepresentation, nullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), is(false));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage should not change",
      storedLoan.getString("itemStatus"), is("In transit"));

    assertThat("Checkin Service Point Id should not change",
      storedLoan.getString("checkinServicePointId"), is(otherServicePoint.getId()));
  }

  @Test
  public void isAvailableWhenCheckedInToReturnItemAtPrimaryServicePointForHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(primaryServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not available",
      itemRepresentation.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    assertThat("in transit item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), is(false));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), is(false));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(primaryServicePoint.getId()));
  }

  @Test
  public void isAvailableWhenCheckedInToReturnItemAtOtherServingServicePointForHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();
    final IndividualResource otherServingServicePoint = servicePointsFixture.cd2();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .servedBy(otherServingServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServingServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not available",
      itemRepresentation.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    assertThat("in transit item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), is(false));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    assertThat("available item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), is(false));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(otherServingServicePoint.getId()));
  }

  @Test
  public void remainsInTransitWhenCheckedInToReceiveAtServicePointNotForHomeLocation() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource firstOtherServicePoint = servicePointsFixture.cd2();

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(firstOtherServicePoint.getId()));

    final IndividualResource secondOtherServicePoint = servicePointsFixture.cd3();

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(secondOtherServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not in transit",
      itemRepresentation.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item should have a destination",
      itemRepresentation.getString("inTransitDestinationServicePointId"),
      is(primaryServicePoint.getId()));

    assertThat("in transit item should have a extended destination properties",
      itemRepresentation.containsKey("inTransitDestinationServicePoint"), is(true));

    final JsonObject destinationServicePoint =
      itemRepresentation.getJsonObject("inTransitDestinationServicePoint");

    assertThat("extended destination properties should include id",
      destinationServicePoint.getString("id"), is(primaryServicePoint.getId()));

    assertThat("extended destination properties should include name",
      destinationServicePoint.getString("name"), is("Circ Desk 1"));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should not be present in response",
      loanRepresentation, nullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not in transit",
      updatedNod.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item in storage should have a destination",
      updatedNod.getString("inTransitDestinationServicePointId"),
      is(primaryServicePoint.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not in transit",
      storedLoan.getString("itemStatus"), is("In transit"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(firstOtherServicePoint.getId()));
  }

  @Test
  public void isNotPlacedInTransitWhenItemHasOpenFulfillableRequest() {

    final IndividualResource primaryServicePoint = servicePointsFixture.cd1();

    final IndividualResource homeLocation =
      locationsFixture.basedUponExampleLocation(builder -> builder
        .servedBy(primaryServicePoint.getId())
        .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource james = usersFixture.james();
    IndividualResource jessica = usersFixture.jessica();

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withTemporaryLocation(homeLocation.getId()));

    final IndividualResource otherServicePoint = servicePointsFixture.cd2();

    final IndividualResource loan = loansFixture.checkOutByBarcode(nod, james);

    requestsFixture.placeHoldShelfRequest(
      nod, jessica, DateTime.now(DateTimeZone.UTC), otherServicePoint.getId());

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(otherServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is not awaiting pickup",
      itemRepresentation.getJsonObject("status").getString("name"), is("Awaiting pickup"));

    assertThat("in transit item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    assertThat("in transit item (in loan) should not have a destination",
      loanRepresentation.getJsonObject("item").containsKey("inTransitDestinationServicePointId"),
      is(false));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is not awaiting pickup",
      updatedNod.getJsonObject("status").getString("name"), is("Awaiting pickup"));

    assertThat("in transit item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"),
      is(false));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not awaiting pickup",
      storedLoan.getString("itemStatus"), is("Awaiting pickup"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(otherServicePoint.getId()));
  }
}

