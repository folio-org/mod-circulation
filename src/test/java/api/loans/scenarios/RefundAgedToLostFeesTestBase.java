package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.AccountMatchers.isClosedCancelled;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.ActualCostRecordMatchers.hasAdditionalInfoForStaff;
import static api.support.matchers.ActualCostRecordMatchers.isInStatus;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimePropertyByPath;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.builders.LostItemFeePolicyBuilder;
import api.support.fixtures.AgeToLostFixture.AgeToLostResult;
import api.support.http.IndividualResource;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;
import lombok.val;

/**
 * This test contains just basic expected scenarios.
 * All possible scenarios are covered by {@code api.loans.scenarios.RefundDeclaredLostFeesTestBase}
 * for {@code Declared lost} items.
 */
public abstract class RefundAgedToLostFeesTestBase extends SpringApiTest {
  protected final String cancellationReason;
  private final String actualCostFeeCancellationReason;

  protected RefundAgedToLostFeesTestBase(String cancellationReason,
    String actualCostFeeCancellationReason) {

    this.cancellationReason = cancellationReason;
    this.actualCostFeeCancellationReason = actualCostFeeCancellationReason;
  }

  @BeforeEach
  public void createOwnerAndFeeTypes() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.overdueFine();
  }

  protected abstract void performActionThatRequiresRefund(AgeToLostResult result,
    ZonedDateTime actionDate);

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

    performActionThatRequiresRefund(result);

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemFee(isClosedCancelled(cancellationReason, setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(cancellationReason, processingFee)));

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
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(cancellationReason, processingFee)));
    assertThat(loan, hasOverdueFine());

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

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

  @Test
  void subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemAlreadyReturned() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemWasRemoved")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result);

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(cancellationReason, processingFee)));

    // Run the charging process again
    ageToLostFixture.chargeFees();

    // make sure fees are not assigned for the loan again
    assertThat(loan, not(hasLostItemProcessingFee(isOpen(processingFee))));

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void shouldCancelActualCostLostItemFeeWhenRefundPeriodWasNotExceeded(double processingFeeAmount) {
    LostItemFeePolicyBuilder policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("test policy")
      .withActualCost(0.0)
      .withLostItemProcessingFee(processingFeeAmount)
      .withFeeRefundInterval(Period.months(6));

    AgeToLostResult ageToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    IndividualResource loan = ageToLostResult.getLoan();

    List<JsonObject> actualCostRecordsBeforeAction = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsBeforeAction, hasSize(1));
    JsonObject recordBeforeAction = actualCostRecordsBeforeAction.get(0);
    assertThat(recordBeforeAction, isInStatus(OPEN));

    assertThat(loan, hasNoLostItemFee());
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());

    performActionThatRequiresRefund(ageToLostResult);

    UUID actualCostRecordId = UUID.fromString(recordBeforeAction.getString("id"));
    JsonObject recordAfterAction = actualCostRecordsClient.get(actualCostRecordId).getJson();
    assertThat(recordAfterAction, isInStatus(CANCELLED));
    assertThat(recordAfterAction, hasAdditionalInfoForStaff(actualCostFeeCancellationReason));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isClosedCancelled(cancellationReason, processingFeeAmount))
      : hasNoLostItemProcessingFee());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void shouldNotCancelActualCostLostItemFeeWhenRefundPeriodWasExceeded(double processingFeeAmount) {
    final int refundPeriodDurationDays = 3;

    LostItemFeePolicyBuilder policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("test policy")
      .withActualCost(0.0)
      .withLostItemProcessingFee(processingFeeAmount)
      .withFeeRefundInterval(Period.days(refundPeriodDurationDays));

    AgeToLostResult ageToLostResult = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    IndividualResource loan = ageToLostResult.getLoan();

    assertThat(loan, hasNoLostItemFee());
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());

    List<JsonObject> actualCostRecordsBeforeAction = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsBeforeAction, hasSize(1));
    JsonObject recordBeforeAction = actualCostRecordsBeforeAction.get(0);
    assertThat(recordBeforeAction, isInStatus(OPEN));

    ZonedDateTime actionDateTime = getZonedDateTime()
      .plusWeeks(8) // lost item fees are charged when loan is 8 weeks overdue
      .plusDays(refundPeriodDurationDays)
      .plusMinutes(1);

    performActionThatRequiresRefund(ageToLostResult, actionDateTime);

    UUID actualCostRecordId = UUID.fromString(recordBeforeAction.getString("id"));
    JsonObject recordAfterAction = actualCostRecordsClient.get(actualCostRecordId).getJson();
    assertThat(recordAfterAction, isInStatus(OPEN));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());
  }

  private void assertThatBillingInformationRemoved(IndividualResource loan) {
    assertThat(loansStorageClient.get(loan).getJson().toString(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }

  private void performActionThatRequiresRefund(AgeToLostResult result) {
    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime());
  }
}
