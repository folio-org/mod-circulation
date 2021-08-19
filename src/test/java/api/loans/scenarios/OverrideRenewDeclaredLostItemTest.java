package api.loans.scenarios;

import static api.support.matchers.ItemMatchers.isCheckedOut;
import static org.hamcrest.MatcherAssert.assertThat;

import org.joda.time.DateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.OverrideRenewalFixture;

public class OverrideRenewDeclaredLostItemTest extends RefundDeclaredLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewDeclaredLostItemTest() {
    super("Cancelled item renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(DateTime actionDate) {
    mockClockManagerToReturnFixedDateTime(actionDate);

    overrideRenewalFixture.overrideRenewalByBarcode(loan, servicePointsFixture.cd1().getId());
  }

  @Test
  public void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }
}
