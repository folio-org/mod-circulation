package api.loans;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.resources.DeclareItemClaimedReturnedAsMissingResource.COMMENT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.DeclareItemClaimedReturnedAsMissingRequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class DeclareItemClaimedReturnedAsMissingApiTests extends APITests {
  private InventoryItemResource item;
  private String loanId;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponSmallAngryPlanet();
    loanId = loansFixture.checkOutByBarcode(item, usersFixture.charlotte())
      .getId().toString();
  }

  @Test
  public void canResolveClaimedReturnedItemAsMissing() {
    final String comment = "testing";

    claimItemReturnedFixture.claimItemReturned(new ClaimItemReturnedRequestBuilder()
      .forLoan(loanId)
      .withItemClaimedReturnedDate(DateTime.now()));

    claimItemReturnedFixture.declareItemClaimedReturnedAsMissing(
      new DeclareItemClaimedReturnedAsMissingRequestBuilder()
        .forLoan(loanId)
        .withComment(comment));

    assertLoanIsClosed(comment);
    assertItemIsMissing();
  }

  @Test
  public void cannotResolveClaimAsMissingWhenItemHasNotClaimedReturnStatus() {
    final Response response = claimItemReturnedFixture
      .attemptDeclareItemClaimedReturnedAsMissing(new DeclareItemClaimedReturnedAsMissingRequestBuilder()
        .forLoan(loanId)
        .withComment("testing"));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Item is not Claimed returned")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", item.getId().toString())));
  }

  @Test
  public void cannotResolveClaimAsMissingWhenLoanIsClosed() {
    loansFixture.checkInByBarcode(item);

    final Response response = claimItemReturnedFixture
      .attemptDeclareItemClaimedReturnedAsMissing(
        new DeclareItemClaimedReturnedAsMissingRequestBuilder()
          .forLoan(loanId)
          .withComment("testing"));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(hasMessage("Loan is closed")));
    assertThat(response.getJson(), hasErrorWith(hasParameter("id", loanId)));
  }

  @Test
  public void cannotResolveClaimAsMissingWhenCommentIsNotProvided() {
    final Response response = claimItemReturnedFixture
      .attemptDeclareItemClaimedReturnedAsMissing(
        new DeclareItemClaimedReturnedAsMissingRequestBuilder()
          .forLoan(loanId));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(),
      hasErrorWith(hasMessage("Comment is a required field")));
    assertThat(response.getJson(), hasErrorWith(hasParameter(COMMENT, null)));
  }

  @Test
  public void cannotResolveClaimAsMissingWhenLoanIsNotFound() {
    final String notExistentLoanId = UUID.randomUUID().toString();

    final Response response = claimItemReturnedFixture
      .attemptDeclareItemClaimedReturnedAsMissing(
        new DeclareItemClaimedReturnedAsMissingRequestBuilder()
          .forLoan(notExistentLoanId)
          .withComment("testing"));

    assertThat(response.getStatusCode(), is(404));
  }

  private void assertLoanIsClosed(String comment) {
    JsonObject actualLoan = loansClient.getById(UUID.fromString(loanId)).getJson();

    assertThat(actualLoan, isClosed());
    assertThat(actualLoan, hasLoanProperty(ACTION, "markedMissing"));
    assertThat(actualLoan, hasLoanProperty(ACTION_COMMENT, comment));
  }

  private void assertItemIsMissing() {
    JsonObject actualItem = loansClient.getById(UUID.fromString(loanId))
      .getJson().getJsonObject("item");

    assertThat(actualItem, hasJsonPath("status.name", "Missing"));
  }
}
