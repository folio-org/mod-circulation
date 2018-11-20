package api.loans;

import static api.support.matchers.UUIDMatcher.is;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Ignore;
import org.junit.Test;

import api.support.APITests;
import io.vertx.core.json.JsonObject;

public class CheckInByBarcodeTests extends APITests {
  @Ignore("Not included in main execution until basic request passing")
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

    final IndividualResource updatedLoan = loansFixture.checkInByBarcode(nod,
      new DateTime(2018, 3, 5, 14, 23, 41, DateTimeZone.UTC),
      checkinServicePointId);

    JsonObject loanRepresentation = updatedLoan.getJson();

    assertThat(loanRepresentation.getString("userId"), is(james.getId().toString()));

    assertThat("Should have return date",
      loanRepresentation.getString("returnDate"), is("2018-03-05T14:23:41.000Z"));

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

    assertThat("item status snapshot in storage is not Available",
      storedLoan.getString("itemStatus"), is("Available"));

    assertThat("Checkin Service Point Id should be stored.",
      storedLoan.getString("checkinServicePointId"), is(checkinServicePointId));
  }
}
