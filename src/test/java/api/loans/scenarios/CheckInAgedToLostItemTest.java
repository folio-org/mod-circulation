package api.loans.scenarios;

import static api.support.matchers.AccountActionsMatchers.arePaymentRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.areTransferRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.isCancelledItemReturnedActionCreated;
import static api.support.matchers.AccountMatchers.isClosedCancelledItemReturned;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeActions;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeActions;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.joda.time.DateTime.now;

import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import lombok.val;

/**
 * This test contains just basic expected scenarios.
 * All possible scenarios are covered by {@code api.loans.scenarios.CheckInDeclaredLostItemTest}
 * for {@code Declared lost} items.
 */
public class CheckInAgedToLostItemTest extends APITests {
  @Before
  public void createOwnerAndFeeTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.overdueFine();
  }

  @Test
  public void shouldRefundPartiallyPaidAmountAndCancelRemaining() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Check in aged to lost loan")
      .chargeItemAgedToLostProcessingFee(processingFee)
      .withSetCost(setCostFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());

    checkInFixture.checkInByBarcode(result.getItem());

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(areTransferRefundActionsCreated(setCostFee)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));

    assertThatBillingInformationRemoved(loan);
  }

  @Test
  public void shouldChargeOverdueFine() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Check in aged to lost loan")
      .chargeItemAgedToLostProcessingFee(processingFee)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    final DateTime returnDate = now(DateTimeZone.UTC).plusMonths(8);
    mockClockManagerToReturnFixedDateTime(returnDate);
    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .on(returnDate)
      .forItem(result.getItem())
      .at(servicePointsFixture.cd1()));

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isRefundedFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      arePaymentRefundActionsCreated(processingFee)));

    assertThat(loan, hasOverdueFine());
  }

  @Test
  public void shouldNotRefundFeesWhenReturnedAfterRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("Check in aged to lost loan")
      .chargeItemAgedToLostProcessingFee(processingFee)
      .withSetCost(setCostFee)
      .refundFeesWithinMinutes(1);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    final IndividualResource loan = loansStorageClient.get(result.getLoan());

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());
    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    final DateTime agedToLostDate = getDateTimePropertyByPath(loan.getJson(),
      "agedToLostDelayedBilling", "agedToLostDate");
    mockClockManagerToReturnFixedDateTime(agedToLostDate.plusMinutes(2));

    checkInFixture.checkInByBarcode(result.getItem());

    assertThat(loan, hasLostItemFee(isTransferredFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(
      not(areTransferRefundActionsCreated(setCostFee))));

    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      not(arePaymentRefundActionsCreated(processingFee))));
  }

  private void assertThatBillingInformationRemoved(IndividualResource loan) {
    assertThat(loansClient.get(loan).getJson().toString(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }
}
