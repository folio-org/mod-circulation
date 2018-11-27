package api.loans;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.withinSecondsAfter;
import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static org.folio.HttpStatus.HTTP_VALIDATION_ERROR;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Seconds;
import org.junit.Test;

import api.support.APITests;
import api.support.CheckInByBarcodeResponse;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeTests extends APITests {
  @Test
  public void canCloseAnOpenLoanByCheckingInTheItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    final UUID checkinServicePointId = UUID.randomUUID();

    final IndividualResource loan = loansFixture.checkOut(nod, james, loanDate);

    DateTime expectedSystemReturnDate = DateTime.now(DateTimeZone.UTC);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(new DateTime(2018, 3, 5, 14 ,23, 41, DateTimeZone.UTC))
        .at(checkinServicePointId));

    JsonObject loanRepresentation = checkInResponse.getLoan();

    assertThat("Closed loan should be present",
      loanRepresentation, notNullValue());

    assertThat(loanRepresentation.getString("userId"), is(james.getId().toString()));

    assertThat("Should have return date",
      loanRepresentation.getString("returnDate"), is("2018-03-05T14:23:41.000Z"));

    assertThat("Should have system return date similar to now",
      loanRepresentation.getString("systemReturnDate"),
      is(withinSecondsAfter(Seconds.seconds(10), expectedSystemReturnDate)));

    assertThat("status is not closed",
      loanRepresentation.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      loanRepresentation.getString("action"), is("checkedin"));

    assertThat("title is taken from item",
      loanRepresentation.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      loanRepresentation.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      loanRepresentation.containsKey("itemStatus"), is(false));

    JsonObject updatedNod = itemsClient.getById(nod.getId()).getJson();

    assertThat("item status is not available",
      updatedNod.getJsonObject("status").getString("name"), is("Available"));

    final JsonObject storedLoan = loansStorageClient.getById(loan.getId()).getJson();

    assertThat("stored loan status is not closed",
      storedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("item status snapshot in storage is not Available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored.",
      storedLoan.getString("checkinServicePointId"), is(checkinServicePointId));
  }

  @Test
  public void cannotCheckInItemThatCannotBeFoundByBarcode() {
    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .withItemBarcode("543593485458")
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "No item with barcode 543593485458 exists")));
  }

  @Test
  public void cannotCheckInWithoutAServicePoint()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .on(DateTime.now()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
        "Checkin request must have a service point id")));
  }

  @Test
  public void cannotCheckInWithoutAnItem()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .on(DateTime.now())
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an item barcode")));
  }

  @Test
  public void cannotCheckInWithoutACheckInDate()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    loansFixture.checkOut(nod, james, loanDate);

    final Response response = loansFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(nod)
        .at(UUID.randomUUID()));

    assertThat(response, hasStatus(HTTP_VALIDATION_ERROR));

    assertThat(response.getJson(), hasErrorWith(hasMessage(
      "Checkin request must have an check in date")));
  }

  @Test
  public void canCheckInAnItemWithoutAnOpenLoan()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    final IndividualResource nod = itemsFixture.basedUponNod();

    final UUID checkInServicePointId = UUID.randomUUID();

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkInServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
  }

  @Test
  public void canCheckInAnItemTwice()
    throws InterruptedException,
    MalformedURLException,
    TimeoutException,
    ExecutionException {

    DateTime loanDate = new DateTime(2018, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();
    final IndividualResource nod = itemsFixture.basedUponNod();

    final UUID checkinServicePointId = UUID.randomUUID();

    loansFixture.checkOut(nod, james, loanDate);

    loansFixture.checkInByBarcode(nod,
      new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkinServicePointId);

    final CheckInByBarcodeResponse checkInResponse = loansFixture.checkInByBarcode(
      nod, new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkinServicePointId);

    assertThat(checkInResponse.getJson().containsKey("loan"), is(false));
  }
}
