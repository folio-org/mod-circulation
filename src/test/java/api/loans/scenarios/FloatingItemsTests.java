package api.loans.scenarios;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.LocationBuilder;
import api.support.fixtures.ItemExamples;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.CoreMatchers;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class FloatingItemsTests extends APITests {

  @Test
  void willSetFloatingItemsTemporaryLocationToFloatingCollectionAtServicePoint() {

    // Floating collection served by service point 'cd1'.
    final IndividualResource floatingCollection = locationsFixture.floatingCollection();

    // Another floating collection serviced by another service point
    final IndividualResource servicePointTwo = servicePointsFixture.cd2();
    IndividualResource otherFloatingCollection = locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Floating collection 2")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("FLOAT2")
        .isFloatingCollection(true)
        .withPrimaryServicePoint(servicePointTwo.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource holdingsInFloatingLocation =
      holdingsFixture.createHoldingsRecord(UUID.randomUUID(), floatingCollection.getId());

    IndividualResource nod = itemsClient.create(ItemExamples.basedUponNod(
      materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId())
      .withBarcode("565578437802")
      .forHolding(holdingsInFloatingLocation.getId()));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder().forItem(nod).at(servicePointTwo.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item should be present in response",
      itemRepresentation, IsNull.notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("barcode should be included for item",
      itemRepresentation.getString("barcode"), CoreMatchers.is("565578437802"));

    assertThat("item status should be 'Available'",
      itemRepresentation.getJsonObject("status").getString("name"), CoreMatchers.is("Available"));

    assertThat("available item should not have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      CoreMatchers.is(false));

    assertThat( "The check-in response should display the item's new location.",
      itemRepresentation.getJsonObject("location").getString("name"), CoreMatchers.is("Floating collection 2"));

    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();

    assertThat( "The staff slip context should display the item's new location.",
      staffSlipContext.getJsonObject("item").getString("effectiveLocationSpecific"), CoreMatchers.is("Floating collection 2"));


    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, IsNull.notNullValue());

    assertThat("item (in loan) should not have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(false));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status should be 'Available'",
      updatedNod.getJsonObject("status").getString("name"), CoreMatchers.is("Available"));

    assertThat("available item in storage should not have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(false));

    assertThat("available item's temporary location set to other floating collection",
      updatedNod.getString("temporaryLocationId"), CoreMatchers.is(otherFloatingCollection.getId().toString()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status should be 'Closed'",
      storedLoan.getJsonObject("status").getString("name"), CoreMatchers.is("Closed"));

    assertThat("item status snapshot in storage should be 'Available'",
      storedLoan.getString("itemStatus"), CoreMatchers.is("Available"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(servicePointTwo.getId()));

  }

  @Test
  void willPutFloatingItemInTransitWhenCheckInServicePointServesNoFloatingCollection() {
    final IndividualResource floatingCollection = locationsFixture.floatingCollection();

    final IndividualResource servicePointTwo = servicePointsFixture.cd2();

    // Location without floating collection, different service point
    locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Location 2")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("FLOAT2")
        .isFloatingCollection(false)
        .withPrimaryServicePoint(servicePointTwo.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource holdingsInFloatingLocation =
      holdingsFixture.createHoldingsRecord(UUID.randomUUID(), floatingCollection.getId());

    IndividualResource nod = itemsClient.create(ItemExamples.basedUponNod(
        materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId())
      .withBarcode("565578437802")
      .forHolding(holdingsInFloatingLocation.getId()));

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointTwo.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    System.out.println(itemsClient.get(nod).getJson().encodePrettily());

    assertThat("item should be present in response",
      itemRepresentation, IsNull.notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("barcode should be included for item",
      itemRepresentation.getString("barcode"), CoreMatchers.is("565578437802"));

    assertThat("item status should be 'In transit'",
      itemRepresentation.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("item should have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      CoreMatchers.is(true));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, IsNull.notNullValue());

    assertThat("item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(true));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status should be 'In transit'",
      updatedNod.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("item in storage should have a destination",
      updatedNod.containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(true));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status should be 'Closed'",
      storedLoan.getJsonObject("status").getString("name"), CoreMatchers.is("Closed"));

    assertThat("item status snapshot in storage should be 'In transit'",
      storedLoan.getString("itemStatus"), CoreMatchers.is("In transit"));

    assertThat("CheckIn Service Point id should be stored",
      storedLoan.getString("checkinServicePointId"), is(servicePointTwo.getId()));

  }

  @Test
  void willPutFloatingItemInTransitWhenHoldRequestWasIssuedAtDifferentServicePoint() {
    final IndividualResource floatingCollection = locationsFixture.floatingCollection();

    final IndividualResource servicePointTwo = servicePointsFixture.cd3();
    final IndividualResource pickUpServicePoint = servicePointsFixture.cd2();


    // Floating collection (location), different service point
    locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Floating collection 2")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("FLOAT2")
        .isFloatingCollection(true)
        .withPrimaryServicePoint(servicePointTwo.getId()));

    // Location without floating collection, with pickup service point
    locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Location 3")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("LOC3")
        .isFloatingCollection(false)
        .withPrimaryServicePoint(pickUpServicePoint.getId()));


    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource instance = instancesFixture.basedUponDunkirk();
    final IndividualResource holdingsInFloatingLocation =
      holdingsFixture.createHoldingsRecord(UUID.randomUUID(), floatingCollection.getId());

    IndividualResource nod = itemsClient.create(ItemExamples.basedUponNod(
        materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId())
      .withBarcode("565578437802")
      .forHolding(holdingsInFloatingLocation.getId()));

    // Check out from floating collection
    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    requestsFixture.placeItemLevelHoldShelfRequest(
      new ItemResource(nod, holdingsInFloatingLocation,instance), jessica, ClockUtil.getZonedDateTime(), pickUpServicePoint.getId());

    // Check in at service point serving a floating collection.
    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointTwo.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    System.out.println(itemsClient.get(nod).getJson().encodePrettily());

    assertThat("item should be present in response",
      itemRepresentation, IsNull.notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("barcode should be included for item",
      itemRepresentation.getString("barcode"), CoreMatchers.is("565578437802"));

    assertThat("item status should be 'In transit'",
      itemRepresentation.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("available item should have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      CoreMatchers.is(true));

    assertThat( "The check-in response should display the item's original location.",
      itemRepresentation.getJsonObject("location").getString("name"), CoreMatchers.is("Floating collection"));

    JsonObject staffSlipContext = checkInResponse.getStaffSlipContext();

    assertThat( "The staff slip context should display the item's original location.",
      staffSlipContext.getJsonObject("item").getString("effectiveLocationSpecific"), CoreMatchers.is("Floating collection"));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, IsNull.notNullValue());

    assertThat("in transit item in storage should have a destination",
      loanRepresentation.getJsonObject("item")
        .getString("inTransitDestinationServicePointId"),
      CoreMatchers.is(pickUpServicePoint.getId().toString()));

    assertThat("item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(true));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status should be 'In transit'",
      updatedNod.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("in transit item in storage should have a destination",
      updatedNod.getString("inTransitDestinationServicePointId"),
      is(pickUpServicePoint.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status should be 'Closed'",
      storedLoan.getJsonObject("status").getString("name"), CoreMatchers.is("Closed"));

    assertThat("item status snapshot in storage should be 'In transit'",
      storedLoan.getString("itemStatus"), CoreMatchers.is("In transit"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(servicePointTwo.getId()));


  }


  @Test
  void willPutItemInTransitIfFloatingLocationWasOverriddenByNonFloatingLocation() {
    // floating collection served by service point cd1.
    final IndividualResource floatingCollection = locationsFixture.floatingCollection();

    final IndividualResource servicePointTwo = servicePointsFixture.cd3();
    final IndividualResource pickUpServicePoint = servicePointsFixture.cd2();

    // Floating collection (location), different service point
    locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Floating collection 2")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("FLOAT2")
        .isFloatingCollection(true)
        .withPrimaryServicePoint(servicePointTwo.getId()));

    // Location without floating collection, with pickup service point
    IndividualResource location3 = locationsFixture.createLocation(
      new LocationBuilder()
        .withName("Location 3")
        .forInstitution(UUID.randomUUID())
        .forCampus(UUID.randomUUID())
        .forLibrary(UUID.randomUUID())
        .withCode("LOC3")
        .isFloatingCollection(false)
        .withPrimaryServicePoint(pickUpServicePoint.getId()));

    final IndividualResource holdingsInFloatingLocation =
      holdingsFixture.createHoldingsRecord(UUID.randomUUID(), floatingCollection.getId());

    IndividualResource nod = itemsClient.create(ItemExamples.basedUponNod(
        materialTypesFixture.book().getId(),
        loanTypesFixture.canCirculate().getId())
      .withPermanentLocation(location3)
      .withBarcode("565578437802")
      .forHolding(holdingsInFloatingLocation.getId()));

    final IndividualResource james = usersFixture.james();

    final IndividualResource loan = checkOutFixture.checkOutByBarcode(nod, james);

    final CheckInByBarcodeResponse checkInResponse = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(servicePointTwo.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    System.out.println(itemsClient.get(nod).getJson().encodePrettily());

    assertThat("item should be present in response",
      itemRepresentation, IsNull.notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("barcode should be included for item",
      itemRepresentation.getString("barcode"), CoreMatchers.is("565578437802"));

    assertThat("item status should be 'In transit'",
      itemRepresentation.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("available item should have a destination",
      itemRepresentation.containsKey("inTransitDestinationServicePointId"),
      CoreMatchers.is(true));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("closed loan should be present in response",
      loanRepresentation, IsNull.notNullValue());

    assertThat("item (in loan) should have a destination",
      loanRepresentation.getJsonObject("item")
        .containsKey("inTransitDestinationServicePointId"), CoreMatchers.is(true));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status should be 'In transit'",
      updatedNod.getJsonObject("status").getString("name"), CoreMatchers.is("In transit"));

    assertThat("in transit item in storage should have a destination",
      updatedNod.getString("inTransitDestinationServicePointId"),
      is(pickUpServicePoint.getId()));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status should be 'Closed'",
      storedLoan.getJsonObject("status").getString("name"), CoreMatchers.is("Closed"));

    assertThat("item status snapshot in storage should be 'In transit'",
      storedLoan.getString("itemStatus"), CoreMatchers.is("In transit"));

    assertThat("Checkin Service Point Id should be stored",
      storedLoan.getString("checkinServicePointId"), is(servicePointTwo.getId()));

  }
}
