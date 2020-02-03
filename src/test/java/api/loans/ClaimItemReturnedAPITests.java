package api.loans;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.APITestContext.getUserId;
import static api.support.http.InterfaceUrls.claimItemReturnedURL;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.representations.ClaimItemReturnedProperties.ITEM_CLAIMED_RETURNED_DATE;
import static org.folio.circulation.support.http.OkapiHeader.USER_ID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.representations.LoanProperties;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.RestAssuredClient;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedAPITests extends APITests {
  private static final String CLAIM_ITEM_RETURNED_REQUEST_NAME = "claim-item-returned-request";

  private InventoryItemResource item;
  private UUID loanId;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponSmallAngryPlanet();
    loanId = loansFixture.checkOutByBarcode(item, usersFixture.charlotte()).getId();
  }

  @Test
  public void canMakeItemClaimedReturnedWithComment() {
    final String comment = "testing";
    final DateTime dateTime = DateTime.now();

    final Response response = loansFixture
      .claimItemReturned(loanId, dateTime, comment);

    assertLoanAndItem(response, comment, dateTime);
  }

  @Test
  public void canMakeItemClaimedReturnedWithoutComment() {
    final DateTime dateTime = DateTime.now();

    final Response response = loansFixture.claimItemReturned(loanId, dateTime);

    assertLoanAndItem(response, null, dateTime);
  }

  @Test
  public void shouldFailWhenLoanIsClosed() {
    final DateTime dateTime = DateTime.now();

    loansFixture.checkInByBarcode(item);

    final Response response = loansFixture.claimItemReturned(loanId, dateTime);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Loan is closed")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", loanId.toString())));
  }

  @Test
  public void shouldFailWhenDateTimeIsMissed() {
    final Response response = restAssuredClient.post(new JsonObject(),
      claimItemReturnedURL(loanId.toString()), CLAIM_ITEM_RETURNED_REQUEST_NAME);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage("Item claimed returned date is a required field")));
    assertThat(response.getJson(), hasErrorWith(hasParameter(ITEM_CLAIMED_RETURNED_DATE, null)));
  }

  @Test
  public void shouldFailWhenNoOkapiUserSet() {
    final RestAssuredClient restAssuredClient =
      new RestAssuredClient(getOkapiHeadersFromContext().withUserId(null));

    final JsonObject claimedReturnedRequest = new JsonObject()
      .put(ITEM_CLAIMED_RETURNED_DATE, DateTime.now().toString());

    final Response response = restAssuredClient.post(claimedReturnedRequest,
      claimItemReturnedURL(loanId.toString()), CLAIM_ITEM_RETURNED_REQUEST_NAME);

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("No okapi user id provided")));
    assertThat(response.getJson(), hasErrorWith(hasParameter(USER_ID, null)));
  }

  @Test
  public void shouldFailIfLoanIsNotFound() {
    final UUID notExistentLoanId = UUID.randomUUID();

    final Response response = loansFixture
      .claimItemReturned(notExistentLoanId, DateTime.now());

    assertThat(response.getStatusCode(), is(404));
  }

  private void assertLoanAndItem(Response response, String comment, DateTime dateTime) {
    JsonObject actualLoan = loansClient.getById(loanId).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Claimed returned"));
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty(LoanProperties.ACTION, "claimedReturned"));
    assertThat(actualLoan, hasLoanProperty(LoanProperties.ACTION_COMMENT, comment));

    final JsonObject claimedReturned = actualLoan
      .getJsonObject(LoanProperties.CLAIMED_RETURNED);

    assertThat(claimedReturned
        .getString(LoanProperties.ClaimedReturned.DATE_TIME), is(dateTime.toString()));
    assertThat(claimedReturned
        .getString(LoanProperties.ClaimedReturned.STAFF_MEMBER_ID), is(getUserId()));
  }
}
