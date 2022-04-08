package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isCancelledItemRenewed;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.OverrideRenewalFixture;

class OverrideRenewDeclaredLostItemTest extends RefundDeclaredLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewDeclaredLostItemTest() {
    super("Cancelled item renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(ZonedDateTime actionDate) {
    mockClockManagerToReturnFixedDateTime(actionDate);

    overrideRenewalFixture.overrideRenewalByBarcode(loan, servicePointsFixture.cd1().getId());
  }

  @Test
  void lostFeeCancellationDoesNotTriggerMarkingItemAsLostAndPaid() {
    useChargeableRefundableLostItemFee(15.00, 0.0);

    declareItemLost();

    performActionThatRequiresRefund();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  void shouldRefundFeesAtAnyPointWhenNoMaximumRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withSetCost(setCostFee)
        .withNoFeeRefundInterval()).getId());

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isCancelledItemRenewed(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));
  }

  @Test
  void shouldRefundPaidAndTransferredFee() {
    final double transferAmount = 6.0;
    final double paymentAmount = 4.0;
    final double setCostFee = transferAmount + paymentAmount;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId(), transferAmount);
    feeFineAccountFixture.payLostItemFee(loan.getId(), paymentAmount);

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isCancelledItemRenewed(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));
  }

  @Test
  void shouldRefundTransferredFee() {
    final double setCostFee = 10.89;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isCancelledItemRenewed(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));
  }

  @Test
  void shouldRefundPaidFee() {
    final double setCostFee = 9.99;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.payLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isCancelledItemRenewed(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));
  }
}
