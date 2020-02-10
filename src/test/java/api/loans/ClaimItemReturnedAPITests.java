package api.loans;

import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasOpenStatus;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.LoanProperties.CLAIMED_RETURNED_DATE;
import static org.folio.circulation.resources.ClaimItemReturnedResource.ITEM_CLAIMED_RETURNED_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedAPITests extends APITests {
  private InventoryItemResource item;
  private String loanId;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponSmallAngryPlanet();
    loanId = loansFixture.checkOutByBarcode(item, usersFixture.charlotte())
      .getId().toString();
  }

  @Test
  public void canClaimItemReturnedWithComment() {
    final String comment = "testing";
    final DateTime dateTime = DateTime.now();

    final Response response = loansFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime)
        .withComment(comment));

    assertLoanAndItem(response, comment, dateTime);
  }

  @Test
  public void canClaimItemReturnedWithoutComment() {
    final DateTime dateTime = DateTime.now();

    final Response response = loansFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime));

    assertLoanAndItem(response, null, dateTime);
  }

  @Test
  public void cannotClaimItemReturnedWhenLoanIsClosed() {
    final DateTime dateTime = DateTime.now();

    loansFixture.checkInByBarcode(item);

    final Response response = loansFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Loan is closed")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", loanId)));
  }

  @Test
  public void cannotClaimItemReturnedWhenDateTimeIsNotProvided() {
    final Response response = loansFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(null));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage("Item claimed returned date is a required field")));
    assertThat(response.getJson(), hasErrorWith(hasParameter(ITEM_CLAIMED_RETURNED_DATE, null)));
  }

  @Test
  public void cannotClaimItemReturnedWhenLoanIsNotFound() {
    final String notExistentLoanId = UUID.randomUUID().toString();

    final Response response = loansFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(notExistentLoanId));

    assertThat(response.getStatusCode(), is(404));
  }

  private void assertLoanAndItem(Response response, String comment, DateTime dateTime) {
    JsonObject actualLoan = loansClient.getById(UUID.fromString(loanId)).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Claimed returned"));
    assertThat(actualLoan, hasOpenStatus());
    assertThat(actualLoan, hasLoanProperty(ACTION, "claimedReturned"));
    assertThat(actualLoan, hasLoanProperty(ACTION_COMMENT, comment));
    assertThat(actualLoan, hasLoanProperty(CLAIMED_RETURNED_DATE, dateTime.toString()));
  }
}
