package api.loans;

import static api.support.matchers.ResponseStatusCodeMatcher.hasStatus;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.folio.HttpStatus.HTTP_NOT_FOUND;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.circulation.domain.representations.LoanProperties.ITEM_ID;
import static org.folio.circulation.resources.ChangeDueDateResource.DUE_DATE;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.ChangeDueDateRequestBuilder;
import api.support.builders.ClaimItemReturnedRequestBuilder;
import api.support.builders.RequestBuilder;
import api.support.http.InventoryItemResource;
import io.vertx.core.json.JsonObject;

public class ChangeDueDateAPITests extends APITests {
  private InventoryItemResource item;
  private IndividualResource loan;
  private DateTime dueDate;

  @Before
  public void setUpItemAndLoan() {
    item = itemsFixture.basedUponNod();
    loan = loansFixture.checkOutByBarcode(item);
    dueDate = DateTime.parse(loan.getJson().getString("dueDate"));
  }

  @Test
  public void canChangeTheDueDate() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    loansFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date is not updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  @Test
  public void cannotChangeDueDateWhenDueDateIsNotProvided() {
    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(null));

    assertResponseOf(response, 422, DUE_DATE);
    assertResponseMessage(response, "Due date is a required field");
  }

  @Test
  public void cannotChangeDueDateWhenLoanIsNotFound() {
    final String nonExistentLoanId = UUID.randomUUID().toString();
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(nonExistentLoanId)
        .withDueDate(newDueDate));

    assertThat(response, hasStatus(HTTP_NOT_FOUND));
  }

  @Test
  public void cannotChangeDueDateWhenClosed() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    loansFixture.checkInByBarcode(item);

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, "id", loan.getId());
    assertResponseMessage(response, "Loan is closed");
  }

  @Test
  public void cannotChangeDueDateWhenDeclaredLost() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    assertThat(loansFixture.declareItemLost(loan.getJson()),
      hasStatus(HTTP_NO_CONTENT));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Declared lost");
  }

  @Test
  public void cannotChangeDueDateWhenClaimedReturned() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    assertThat(loansFixture.claimItemReturned(
      new ClaimItemReturnedRequestBuilder()
        .forLoan(loan.getId().toString())
       ), hasStatus(HTTP_NO_CONTENT));

    (new ChangeDueDateRequestBuilder()).forLoan(loan.getId().toString());

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(newDueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Claimed returned");
  }

  @Test
  public void cannotReapplyDueDateWhenClaimedReturned() {
    assertThat(loansFixture.claimItemReturned(
      new ClaimItemReturnedRequestBuilder()
        .forLoan(loan.getId().toString())
      ), hasStatus(HTTP_NO_CONTENT));

    final Response response = loansFixture
      .attemptChangeDueDate(new ChangeDueDateRequestBuilder()
        .forLoan(loan.getId())
        .withDueDate(dueDate));

    assertResponseOf(response, 422, ITEM_ID, item.getId());
    assertResponseMessage(response, "item is Claimed returned");
  }

  @Test
  public void canChangeDueDateWithOpenRequest() {
    final DateTime newDueDate = dueDate.plus(Period.days(14));

    requestsFixture.place(new RequestBuilder()
      .hold()
      .forItem(item)
      .by(usersFixture.steve())
      .fulfilToHoldShelf(servicePointsFixture.cd1()));

    loansFixture.changeDueDate(new ChangeDueDateRequestBuilder()
      .forLoan(loan.getId())
      .withDueDate(newDueDate));

    Response response = loansClient.getById(loan.getId());

    JsonObject updatedLoan = response.getJson();

    assertThat("due date is not updated",
      updatedLoan.getString("dueDate"), isEquivalentTo(newDueDate));
  }

  private void assertResponseOf(Response response, int code,
      String key) {

    assertResponseOf(response, code, key, (String) null);
  }

  private void assertResponseOf(Response response, int code,
      String key, UUID value) {

    assertResponseOf(response, code, key, value.toString());
  }

  private void assertResponseOf(Response response, int code,
      String key, String value) {

    assertThat(response.getStatusCode(), is(code));
    assertThat(response.getJson(), hasErrorWith(hasParameter(key, value)));
  }

  private void assertResponseMessage(Response response, String message) {
    assertThat(response.getJson(), hasErrorWith(hasMessage(message)));
  }
}
