package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class CheckInDeclaredLostItemTest extends RefundDeclaredLostFeesTestBase {
  public CheckInDeclaredLostItemTest() {
    super("Cancelled item returned");
  }

  @Override
  protected void performActionThatRequiresRefund(DateTime actionDate) {
    mockClockManagerToReturnFixedDateTime(actionDate);

    final JsonObject loan = checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(actionDate))
      .getLoan();

    assertThat(loan, notNullValue());
  }

  @Test
  void shouldRefundOnlyLastLoanForLostAndPaidItem() {
    final double firstFee = 20.00;
    final double secondFee = 30.00;

    useChargeableRefundableLostItemFee(firstFee, 0.0);

    final IndividualResource firstLoan = declareItemLost();
    mockClockManagerToReturnFixedDateTime(ClockUtil.getDateTime()
      .plusMinutes(2));
    // Item fee won't be cancelled, because refund period is exceeded
    checkInFixture.checkInByBarcode(item);
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    mockClockManagerToReturnDefaultDateTime();
    declareItemLost(secondFee);
    resolveLostItemFee();
    checkInFixture.checkInByBarcode(item);

    assertThat(firstLoan, hasLostItemFee(isOpen(firstFee)));
    assertThat(loan, hasLostItemFee(isRefundedFully(secondFee)));

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldFailIfNoLoanForLostAndPaidItem() {
    final double setCost = 20.00;

    declareItemLost(setCost);
    resolveLostItemFee();

    // Remove the loan from storage
    loansFixture.deleteLoan(loan.getId());

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item is lost however there is no aged to lost nor declared lost loan found"),
      hasParameter("itemId", item.getId().toString()))));
  }

  @Test
  void shouldFailIfLastLoanIsNotDeclaredLostForLostAndPaidItem() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    checkInFixture.checkInByBarcode(item);

    // Update the item status in storage
    itemsClient.replace(item.getId(), itemsClient.get(item).getJson().copy()
      .put("status", new JsonObject().put("name", "Lost and paid")));

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Last loan for lost item is neither aged to lost nor declared lost"),
      hasParameter("loanId", loan.getId().toString()))));
  }

  @Test
  void shouldRefundPaidAmountForLostAndPaidItem() {
    final double setCostFee = 10.00;

    declareItemLost(setCostFee);
    resolveLostItemFee();

    final var response = checkInFixture.checkInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .forItem(item)
        .at(servicePointsFixture.cd1()));

    assertThat(response.getLoan(), nullValue());
    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());
  }
}
