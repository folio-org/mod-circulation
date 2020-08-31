package api.loans.scenarios;

import static api.support.matchers.AccountActionsMatchers.arePaymentRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.isCancelledItemReturnedActionCreated;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeActions;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.joda.time.DateTime.now;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInDeclaredLostItemTest extends RefundLostItemFeesTestBase {
  @Override
  protected void performActionThatRequiresRefund() {
    checkInFixture.checkInByBarcode(item);
  }

  @Override
  protected void performActionThatRequiresRefund(DateTime actionDate) {
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(actionDate));
  }

  @Test
  public void shouldRefundOnlyLastLoanForLostAndPaidItem() {
    final double firstFee = 20.00;
    final double secondFee = 30.00;

    useChargeableRefundableLostItemFee(firstFee, 0.0);

    final IndividualResource firstLoan = declareItemLost();
    mockClockManagerToReturnFixedDateTime(now(DateTimeZone.UTC).plusMinutes(2));
    // Item fee won't be cancelled, because refund period is exceeded
    checkInFixture.checkInByBarcode(item);
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    mockClockManagerToReturnDefaultDateTime();
    declareItemLost(secondFee);
    resolveLostItemFee();
    checkInFixture.checkInByBarcode(item);

    assertThat(firstLoan, hasLostItemFee(isOpen(firstFee)));
    assertThat(firstLoan, hasLostItemFeeActions(
      not(isCancelledItemReturnedActionCreated(firstFee))));

    assertThat(loan, hasLostItemFee(isRefundedFully(secondFee)));
    assertThat(loan, hasLostItemFeeActions(arePaymentRefundActionsCreated(secondFee)));
  }

  @Test
  public void shouldFailIfNoLoanForLostAndPaidItem() {
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
      hasMessage("Item is lost however there is no declared lost loan found"),
      hasParameter("itemId", item.getId().toString()))));
  }

  @Test
  public void shouldFailIfLastLoanIsNotDeclaredLostForLostAndPaidItem() {
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
      hasMessage("Last loan for lost item is not declared lost"),
      hasParameter("loanId", loan.getId().toString()))));
  }

  @Test
  public void shouldRefundPaidAmountForLostAndPaidItem() {
    final double setCostFee = 10.00;

    declareItemLost(setCostFee);
    resolveLostItemFee();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(arePaymentRefundActionsCreated(setCostFee)));
  }

  @Test
  public void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());
  }
}
