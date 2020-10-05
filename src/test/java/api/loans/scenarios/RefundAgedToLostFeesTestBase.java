package api.loans.scenarios;

import static api.support.matchers.AccountActionsMatchers.arePaymentRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.areTransferRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.isCancelledItemReturnedActionCreated;
import static api.support.matchers.AccountMatchers.isClosedCancelledItemReturned;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeActions;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeActions;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static api.support.spring.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;

import api.support.http.IndividualResource;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

import api.support.fixtures.AgeToLostFixture;
import api.support.spring.SpringApiTest;
import lombok.val;

/**
 * This test contains just basic expected scenarios.
 * All possible scenarios are covered by {@code api.loans.scenarios.RefundDeclaredLostFeesTestBase}
 * for {@code Declared lost} items.
 */
public abstract class RefundAgedToLostFeesTestBase extends SpringApiTest {
  @Before
  public void createOwnerAndFeeTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.overdueFine();
  }

  protected abstract void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
    DateTime actionDate);

  @Test
  public void shouldRefundPartiallyPaidAmountAndCancelRemaining() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldRefundPartiallyPaidAmountAndCancelRemaining")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withSetCost(setCostFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());

    performActionThatRequiresRefund(result);

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(areTransferRefundActionsCreated(setCostFee)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void shouldChargeOverdueFine() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldChargeOverdueFine")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result, now(UTC).plusMonths(8));

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isRefundedFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      arePaymentRefundActionsCreated(processingFee)));

    assertThat(loan, hasOverdueFine());

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void shouldNotRefundFeesWhenReturnedAfterRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;
    final int feeRefundPeriodMinutes = 1;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldNotRefundFeesWhenReturnedAfterRefundPeriod")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withSetCost(setCostFee)
      .refundFeesWithinMinutes(feeRefundPeriodMinutes);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    final IndividualResource loan = loansStorageClient.get(result.getLoan());

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());
    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    final DateTime feeRefundDisallowedDate = getDateTimePropertyByPath(loan.getJson(),
      "agedToLostDelayedBilling", "agedToLostDate")
      .plusMinutes(feeRefundPeriodMinutes + 1);

    performActionThatRequiresRefund(result, feeRefundDisallowedDate);

    assertThat(loan, hasLostItemFee(isTransferredFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(
      not(areTransferRefundActionsCreated(setCostFee))));

    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      not(arePaymentRefundActionsCreated(processingFee))));

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  @Test
  public void subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemAlreadyReturned() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemWasRemoved")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result);

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isRefundedFully(processingFee)));

    // Run the charging process again
    ageToLostFixture.chargeFees();

    // make sure fees are not assigned for the loan again
    assertThat(loan, not(hasLostItemProcessingFee(isOpen(processingFee))));

    assertThatPublishedLoanLogRecordEventsAreValid();
  }

  private void assertThatBillingInformationRemoved(IndividualResource loan) {
    assertThat(loansStorageClient.get(loan).getJson().toString(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }

  private void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result) {
    performActionThatRequiresRefund(result, now(UTC));
  }
}
