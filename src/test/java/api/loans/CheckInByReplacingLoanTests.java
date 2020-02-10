package api.loans;

import static api.support.matchers.UUIDMatcher.is;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasUUIDParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.MalformedURLException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.LoanBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInByReplacingLoanTests extends APITests {
  @Test
  public void canCompleteALoanByReturningTheItem() {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    IndividualResource loan = loansFixture.createLoan(new LoanBuilder()
      .withLoanDate(loanDate)
      .withUserId(james.getId())
      .withItem(itemsFixture.basedUponNod()));

    UUID checkinServicePointId = servicePointsFixture.cd1().getId();

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin")
      .put("checkinServicePointId", checkinServicePointId.toString())
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41, DateTimeZone.UTC)
        .toString(ISODateTimeFormat.dateTime()));

    loansFixture.replaceLoan(loan.getId(), returnedLoan);

    Response updatedLoanResponse = loansClient.getById(loan.getId());

    JsonObject updatedLoan = updatedLoanResponse.getJson();

    assertThat(updatedLoan.getString("userId"), is(james.getId().toString()));

    assertThat(updatedLoan.getString("returnDate"),
      is("2017-03-05T14:23:41.000Z"));

    assertThat("status is not closed",
      updatedLoan.getJsonObject("status").getString("name"), is("Closed"));

    assertThat("action is not checkedin",
      updatedLoan.getString("action"), is("checkedin"));

    assertThat("title is taken from item",
      updatedLoan.getJsonObject("item").getString("title"),
      is("Nod"));

    assertThat("barcode is taken from item",
      updatedLoan.getJsonObject("item").getString("barcode"),
      is("565578437802"));

    assertThat("Should not have snapshot of item status, as current status is included",
      updatedLoan.containsKey("itemStatus"), is(false));

    JsonObject item = itemsClient.getById(itemsFixture.basedUponNod().getId()).getJson();

    assertThat("item status is not available",
      item.getJsonObject("status").getString("name"), is("Available"));

    assertThat("item status snapshot in storage is not Available",
      loansStorageClient.getById(loan.getId()).getJson().getString("itemStatus"),
      is("Available"));

    assertThat("Checkin Service Point Id should be stored.",
      loansStorageClient.getById(loan.getId()).getJson().getString("checkinServicePointId"),
      is(checkinServicePointId));
  }

  @Test
  public void cannotCloseALoanWithoutAServicePoint() {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    IndividualResource loan = loansFixture.createLoan(
      new LoanBuilder().withLoanDate(loanDate)
        .withUserId(james.getId()).withItem(itemsFixture.basedUponNod()));

    JsonObject returnedLoan = loan.copyJson();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin")
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41,
        DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime()));

    Response putResponse = loansFixture.attemptToReplaceLoan(loan.getId(),
        returnedLoan);

    assertThat(putResponse.getJson(), hasErrorWith(hasMessage(
      "A Closed loan must have a Checkin Service Point")));
  }

  @Test
  public void cannotUpdateALoanWithAnUnknownServicePoint() {

    DateTime loanDate = new DateTime(2017, 3, 1, 13, 25, 46, DateTimeZone.UTC);

    final IndividualResource james = usersFixture.james();

    IndividualResource loan = loansFixture.createLoan(
      new LoanBuilder().withLoanDate(loanDate)
        .withUserId(james.getId()).withItem(itemsFixture.basedUponNod()));

    JsonObject returnedLoan = loan.copyJson();

    final UUID unknownServicePointId = UUID.randomUUID();

    returnedLoan
      .put("status", new JsonObject().put("name", "Closed"))
      .put("action", "checkedin")
      .put("checkinServicePointId", unknownServicePointId.toString())
      .put("returnDate", new DateTime(2017, 3, 5, 14, 23, 41,
        DateTimeZone.UTC).toString(ISODateTimeFormat.dateTime()));

    Response putResponse = loansFixture.attemptToReplaceLoan(loan.getId(),
      returnedLoan);

    assertThat(putResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Check In Service Point does not exist"),
      hasUUIDParameter("checkinServicePointId", unknownServicePointId))));
  }
}
