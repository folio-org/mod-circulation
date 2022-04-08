package api.loans.scenarios;

import java.time.ZonedDateTime;

import api.support.http.IndividualResource;
import lombok.val;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.fixtures.AgeToLostFixture;
import api.support.fixtures.OverrideRenewalFixture;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.AccountMatchers.isCancelledItemRenewed;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

class OverrideRenewAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewAgedToLostItemTest() {
    super("Cancelled item renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
     ZonedDateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    overrideRenewalFixture.overrideRenewalByBarcode(result.getLoan(),
      servicePointsFixture.cd1().getId());
  }

  @Test
  void subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemAlreadyReturned() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemWasRemoved")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime());

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));

    // Run the charging process again
    ageToLostFixture.chargeFees();

    // make sure fees are not assigned for the loan again
    assertThat(loan, not(hasLostItemProcessingFee(isOpen(processingFee))));

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldRefundPartiallyPaidAmountAndCancelRemaining() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldRefundPartiallyPaidAmountAndCancelRemaining")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withSetCost(setCostFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime());

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemFee(isCancelledItemRenewed(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldChargeOverdueFine() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldChargeOverdueFine")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime().plusMonths(8));

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemRenewed(processingFee)));
    assertThat(loan, hasOverdueFine());

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  private void assertThatBillingInformationRemoved(IndividualResource loan) {
    assertThat(loansStorageClient.get(loan).getJson().toString(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }
}
