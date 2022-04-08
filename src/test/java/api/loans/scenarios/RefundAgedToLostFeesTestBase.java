package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;

import java.time.ZonedDateTime;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.fixtures.AgeToLostFixture;
import api.support.http.IndividualResource;
import api.support.spring.SpringApiTest;
import lombok.val;

/**
 * This test contains just basic expected scenarios.
 * All possible scenarios are covered by {@code api.loans.scenarios.RefundDeclaredLostFeesTestBase}
 * for {@code Declared lost} items.
 */
public abstract class RefundAgedToLostFeesTestBase extends SpringApiTest {
  private final String cancellationReason;

  protected RefundAgedToLostFeesTestBase(String cancellationReason) {
    this.cancellationReason = cancellationReason;
  }

  @BeforeEach
  public void createOwnerAndFeeTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.overdueFine();
  }

  protected abstract void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
    ZonedDateTime actionDate);

  @Test
  void shouldChargeOverdueFineIfNoFeesChargedYet() {
    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldChargeOverdueFine")
      .chargeProcessingFeeWhenAgedToLost(12.99)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createAgedToLostLoan(policy);

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime().plusMonths(8));

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasOverdueFine());
    assertThat(result.getLoan(), hasNoLostItemFee());
    assertThat(result.getLoan(), hasNoLostItemProcessingFee());

    assertThatBillingInformationRemoved(loan);
  }

  @Test
  void shouldNotRefundFeesWhenReturnedAfterRefundPeriod() {
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

    final ZonedDateTime feeRefundDisallowedDate = getDateTimePropertyByPath(loan.getJson(),
      "agedToLostDelayedBilling", "agedToLostDate")
      .plusMinutes(feeRefundPeriodMinutes + 1);

    performActionThatRequiresRefund(result, feeRefundDisallowedDate);

    assertThat(loan, hasLostItemFee(isTransferredFully(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));

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
