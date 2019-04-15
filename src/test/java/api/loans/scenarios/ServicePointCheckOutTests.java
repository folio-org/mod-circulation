package api.loans.scenarios;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;

import java.net.MalformedURLException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.CheckOutByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

public class ServicePointCheckOutTests extends APITests {
  @Test
  public void isInTransitWhenCheckedOutAtNonPickupServicePoint()
      throws MalformedURLException, InterruptedException, TimeoutException,
      ExecutionException {
    final IndividualResource checkInServicePoint = servicePointsFixture.cd1();
    final IndividualResource checkOutServicePoint = servicePointsFixture.cd2();
    final IndividualResource primaryServicePoint = servicePointsFixture.cd4();
    final IndividualResource requestServicePoint = primaryServicePoint;

    final IndividualResource james = usersFixture.james();
    final IndividualResource jessica = usersFixture.jessica();

    final IndividualResource homeLocation =
        locationsFixture.basedUponExampleLocation(builder -> builder
          .servedBy(primaryServicePoint.getId())
          .withPrimaryServicePoint(primaryServicePoint.getId()));

    final IndividualResource nod = itemsFixture.basedUponNod(builder ->
      builder.withPermanentLocation(homeLocation.getId()));

    loansFixture.checkOutByBarcode(nod, james);

    final IndividualResource request = requestsFixture.placeHoldShelfRequest(nod, jessica,
        DateTime.now(DateTimeZone.UTC), requestServicePoint.getId());

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
        new CheckInByBarcodeRequestBuilder()
          .forItem(nod)
          .at(checkInServicePoint.getId()));

    JsonObject itemRepresentation = checkInResponse.getItem();

    assertThat("item status is in transit",
        itemRepresentation.getJsonObject("status").getString("name"), is("In transit"));

    assertThat("in transit item should have a destination",
        itemRepresentation.getString("inTransitDestinationServicePointId"),
        is(requestServicePoint.getId()));

    assertThat("in transit item should have a extended destination properties",
        itemRepresentation.containsKey("inTransitDestinationServicePoint"), is(true));

    final JsonObject destinationServicePoint =
        itemRepresentation.getJsonObject("inTransitDestinationServicePoint");

    assertThat("extended destination properties should include id",
        destinationServicePoint.getString("id"), is(requestServicePoint.getId()));

    assertThat("extended destination properties should include name",
        destinationServicePoint.getString("name"), is("Circ Desk 4"));

    final IndividualResource checkOutResponse = loansFixture.checkOutByBarcode(
        new CheckOutByBarcodeRequestBuilder()
          .forItem(nod)
          .to(jessica)
          .at(checkOutServicePoint.getId()));

    itemRepresentation = checkOutResponse.getJson().getJsonObject("item");

    assertThat("item should be present in response",
      itemRepresentation, notNullValue());

    assertThat("ID should be included for item",
      itemRepresentation.getString("id"), is(nod.getId()));

    assertThat("title is included for item",
      itemRepresentation.getString("title"), is("Nod"));

    assertThat("barcode is included for item",
      itemRepresentation.getString("barcode"), is("565578437802"));

    assertThat("item status is checked out",
      itemRepresentation.getJsonObject("status").getString("name"), is("Checked out"));

    JsonObject loanRepresentation = checkOutResponse.getJson();

    assertThat("closed loan should be present in response",
      loanRepresentation, notNullValue());

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("stored item status is checked out",
      updatedNod.getJsonObject("status").getString("name"), is("Checked out"));

    final JsonObject storedLoan = loansStorageClient.getById(checkOutResponse.getId()).getJson();

    assertThat("stored loan status is open",
      storedLoan.getJsonObject("status").getString("name"), is("Open"));

    assertThat("item status snapshot in storage is checked out",
      storedLoan.getString("itemStatus"), is("Checked out"));

    final JsonObject storedRequest = requestsClient.getById(request.getId()).getJson();

    assertThat("request status snapshot in storage is closed - filled",
        storedRequest.getString("status"), is("Closed - Filled"));
  }
}
