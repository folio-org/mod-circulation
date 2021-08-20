package api.loans;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.matchers.EventMatchers.isValidItemClaimedReturnedEvent;
import static api.support.matchers.EventTypeMatchers.ITEM_CLAIMED_RETURNED;
import static api.support.matchers.LoanMatchers.hasLoanProperty;
import static api.support.matchers.LoanMatchers.hasStatus;
import static api.support.matchers.LoanMatchers.isOpen;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasNullParameter;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION;
import static org.folio.circulation.domain.representations.LoanProperties.ACTION_COMMENT;
import static org.folio.circulation.domain.representations.LoanProperties.CLAIMED_RETURNED_DATE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.fakes.FakePubSub;
import api.support.http.IndividualResource;
import api.support.http.ItemResource;
import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedAPITests extends APITests {
  private ItemResource item;
  private IndividualResource loan;
  private String loanId;

  @BeforeEach
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponSmallAngryPlanet();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());
    loanId = loan.getId().toString();
  }

  @Test
  void canClaimItemReturnedWithComment() {
    final String comment = "testing";
    final DateTime dateTime = DateTime.now();

    final Response response = claimItemReturnedFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime)
        .withComment(comment));

    assertLoanAndItem(response, comment, dateTime);
  }

  @Test
  void canClaimItemReturnedWithoutComment() {
    final DateTime dateTime = DateTime.now();

    final Response response = claimItemReturnedFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime));

    assertLoanAndItem(response, null, dateTime);
  }

  @Test
  void cannotClaimItemReturnedWhenLoanIsClosed() {
    final DateTime dateTime = DateTime.now();

    checkInFixture.checkInByBarcode(item);

    final Response response = claimItemReturnedFixture
      .attemptClaimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Loan is closed"),
      hasParameter("loanId", loanId))));
  }

  @Test
  void cannotClaimItemReturnedWhenDateTimeIsNotProvided() {
    final Response response = claimItemReturnedFixture
      .attemptClaimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(null));

    assertThat(response.getStatusCode(), is(422));
    assertThat(response.getJson(), hasErrorWith(allOf(
      hasMessage("Item claimed returned date is a required field"),
      hasNullParameter("itemClaimedReturnedDate"))));
  }

  @Test
  void cannotClaimItemReturnedWhenLoanIsNotFound() {
    final String notExistentLoanId = UUID.randomUUID().toString();

    final Response response = claimItemReturnedFixture
      .attemptClaimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(notExistentLoanId));

    assertThat(response.getStatusCode(), is(404));
  }

  @Test
  void itemClaimedReturnedEventIsPublished() {
    final DateTime dateTime = DateTime.now();

    final Response response = claimItemReturnedFixture
      .claimItemReturned(new ClaimItemReturnedRequestBuilder()
        .forLoan(loanId)
        .withItemClaimedReturnedDate(dateTime));

    assertLoanAndItem(response, null, dateTime);

    // Five events are expected: one for check-out one for log event, one for the claim
    // and one for log records
    final var publishedEvents = Awaitility.await()
      .atMost(1, TimeUnit.SECONDS)
      .until(FakePubSub::getPublishedEvents, hasSize(4));

    final var event = publishedEvents.findFirst(byEventType(ITEM_CLAIMED_RETURNED));

    assertThat(event, isValidItemClaimedReturnedEvent(loan.getJson()));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  private void assertLoanAndItem(Response response, String comment, DateTime dateTime) {
    JsonObject actualLoan = loansClient.getById(UUID.fromString(loanId)).getJson();
    JsonObject actualItem = actualLoan.getJsonObject("item");

    assertThat(response.getStatusCode(), is(204));
    assertThat(actualItem, hasStatus("Claimed returned"));
    assertThat(actualLoan, isOpen());
    assertThat(actualLoan, hasLoanProperty(ACTION, is("claimedReturned")));
    assertThat(actualLoan, hasLoanProperty(ACTION_COMMENT, is(comment)));
    assertThat(actualLoan, hasLoanProperty(CLAIMED_RETURNED_DATE, isEquivalentTo(dateTime)));
  }
}
