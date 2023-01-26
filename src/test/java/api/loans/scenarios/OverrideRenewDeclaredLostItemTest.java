package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFeeActualCost;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.OverrideRenewalFixture;

class OverrideRenewDeclaredLostItemTest extends RefundDeclaredLostFeesTestBase {

  private static final String RENEWED_THROUGH_OVERRIDE = "renewedThroughOverride";

  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewDeclaredLostItemTest() {
    super("Cancelled item renewed", "Lost item was renewed");
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
  void canOverrideRenewalAfterDeclaredLostAndRefundsWithLostItemActualCostFee() {
    final double itemFeeActualCost = 10.0;
    final double itemProcessingFee = 5.0;
    declareItemLostWithActualCost(itemFeeActualCost, itemProcessingFee);

    assertThat(feeFineActionsClient.getAll(), hasSize(2));
    assertThat(loan, hasLostItemFeeActualCost(isOpen(itemFeeActualCost)));
    assertThat(loan, hasLostItemProcessingFee(isOpen(itemProcessingFee)));

    feeFineAccountFixture.payLostItemActualCostFee(loan.getId(), 3.0);
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId(), 3.0);
    overrideRenewalFixture.overrideRenewalByBarcode(loan, servicePointsFixture.cd1().getId());

    assertThat(loansClient.get(loan.getId()).getJson().getString("action"),
      is(RENEWED_THROUGH_OVERRIDE));
    assertThat(loan, hasLostItemFeeActualCost(isClosedCancelled(itemFeeActualCost)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(itemProcessingFee)));
  }

  @Test
  void canOverrideRenewalAfterDeclaredLostAndRefundsWithOnlyLostItemActualCostFee() {
    final double itemFeeActualCost = 10.0;
    declareItemLostWithActualCost(itemFeeActualCost, 0.0);

    assertThat(feeFineActionsClient.getAll(), hasSize(1));
    assertThat(loan, hasLostItemFeeActualCost(isOpen(itemFeeActualCost)));

    feeFineAccountFixture.payLostItemActualCostFee(loan.getId(), 3.0);
    overrideRenewalFixture.overrideRenewalByBarcode(loan, servicePointsFixture.cd1().getId());

    assertThat(loansClient.get(loan.getId()).getJson().getString("action"),
      is(RENEWED_THROUGH_OVERRIDE));
    assertThat(loan, hasLostItemFeeActualCost(isClosedCancelled(itemFeeActualCost)));
  }
}