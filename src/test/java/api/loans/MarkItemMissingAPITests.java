package api.loans;

import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.resources.MarkItemMissingResource.COMMENT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.MarkItemMissingRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

public class MarkItemMissingAPITests extends APITests {
  private InventoryItemResource item;
  private String loanId;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponSmallAngryPlanet();
    loanId = loansFixture.checkOutByBarcode(item, usersFixture.charlotte())
      .getId().toString();
  }

  @Test
  public void canMarkItemMissing() {
    final String comment = "testing";

    loansFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(loanId)
      .withItemClaimedReturnedDate(DateTime.now()));

    final Response response = loansFixture
      .markItemMissing(new MarkItemMissingRequestBuilder()
        .forLoan(loanId)
        .withComment(comment));

    assertLoanAndItem(response, comment);
  }

  @Test
  public void cannotMarkItemMissingWhenItemHasNotClaimedReturnStatus() {
    final Response response = loansFixture
      .attemptMarkItemMissing(new MarkItemMissingRequestBuilder()
        .forLoan(loanId)
        .withComment("testing"));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Item is not Claimed returned")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", item.getId().toString())));
  }

  @Test
  public void cannotMarkItemMissingWhenLoanIsClosed() {
    loansFixture.checkInByBarcode(item);

    final Response response = loansFixture
      .attemptMarkItemMissing(new MarkItemMissingRequestBuilder()
        .forLoan(loanId)
        .withComment("testing"));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Loan is closed")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", loanId)));
  }

  @Test
  public void cannotMarkItemMissingWhenCommentIsNotProvided() {
    final Response response = loansFixture
      .attemptMarkItemMissing(new MarkItemMissingRequestBuilder()
        .forLoan(loanId));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage("Comment is a required field")));
    assertThat(response.getJson(), hasErrorWith(hasParameter(COMMENT, null)));
  }

  @Test
  public void cannotMarkedItemMissingWhenLoanIsNotFound() {
    final String notExistentLoanId = UUID.randomUUID().toString();

    final Response response = loansFixture
      .attemptMarkItemMissing(new MarkItemMissingRequestBuilder()
        .forLoan(notExistentLoanId)
        .withComment("testing"));

    assertThat(response.getStatusCode(), is(404));
  }

  private void assertLoanAndItem(Response response, String comment) {
    JsonObject actualLoan = loansClient.getById(UUID.fromString(loanId)).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Missing"));
    assertThat(actualLoan, isClosed());
    assertThat(actualLoan, hasLoanProperty(ACTION, "markedMissing"));
    assertThat(actualLoan, hasLoanProperty(ACTION_COMMENT, comment));
  }
}
