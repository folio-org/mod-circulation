package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.ActualCostRecordMatchers.hasAdditionalInfoForStaff;
import static api.support.matchers.ActualCostRecordMatchers.isInStatus;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static api.support.matchers.LoanMatchers.isClosed;
import static java.lang.String.format;
import static org.folio.circulation.domain.ActualCostRecord.Status.CANCELLED;
import static org.folio.circulation.domain.ActualCostRecord.Status.OPEN;
import static org.folio.circulation.support.utils.ClockUtil.getClock;
import static org.folio.circulation.support.utils.ClockUtil.getZonedDateTime;
import static org.folio.circulation.support.utils.ClockUtil.setClock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import api.support.builders.AccountBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.IndividualResource;
import api.support.matchers.AccountMatchers;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;

public abstract class RefundDeclaredLostFeesTestBase extends SpringApiTest {
  protected final IndividualResource item = itemsFixture.basedUponNod();
  protected final String cancellationReason;
  protected final String actualCostFeeCancellationReason;
  protected IndividualResource loan;

  public RefundDeclaredLostFeesTestBase(String cancellationReason,
    String actualCostFeeCancellationReason) {

    this.cancellationReason = cancellationReason;
    this.actualCostFeeCancellationReason = actualCostFeeCancellationReason;
  }

  protected void performActionThatRequiresRefund() {
    performActionThatRequiresRefund(ClockUtil.getZonedDateTime());
  }

  protected abstract void performActionThatRequiresRefund(ZonedDateTime actionDate);

  @BeforeEach
  public void activateChargeableLostItemFeePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());
  }

  @Test
  void shouldCancelItemFeeOnlyWhenNoOtherFeesCharged() {
    final double itemFee = 15.00;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withSetCost(itemFee)).getId());

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelled(itemFee)));
  }

  @Test
  void shouldCancelItemProcessingFeeOnlyWhenNoOtherFeesChargedAndNoPaymentsMade() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldNotRefundProcessingFeeWhenPolicyStatesNotTo() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .doNotRefundProcessingFeeWhenReturned()
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
  }

  @Test
  void shouldNotRefundFeesWhenReturnedAfterRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    performActionThatRequiresRefund(ClockUtil.getZonedDateTime().plusMinutes(2));

    assertThat(loan, hasLostItemFee(isTransferredFully(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
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

    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldCancelBothItemAndProcessingFeesWhenNeitherHaveBeenPaid() {
    final double processingFee = 5.0;
    final double itemFee = 10.0;

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelled(itemFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldRefundTransferredFee() {
    final double setCostFee = 10.89;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldRefundPaidFee() {
    final double setCostFee = 9.99;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.payLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
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

    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldRefundPartiallyPaidAndTransferredFeeAndCancelRemainingAmount() {
    final double transferAmount = 6.0;
    final double paymentAmount = 4.0;
    final double remainingAmount = 5.99;
    final double setCostFee = transferAmount + paymentAmount + remainingAmount;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId(), transferAmount);
    feeFineAccountFixture.payLostItemFee(loan.getId(), paymentAmount);

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelled(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
  }

  @Test
  void shouldChargeOverdueFineWhenStatedByPolicyAndLostFeesCanceled() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withNoChargeAmountItem()
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    performActionThatRequiresRefund(ClockUtil.getZonedDateTime().plusMonths(2));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
    assertThat(loan, hasOverdueFine());
  }

  @Test
  void shouldNotChargeOverdueFineWhenNotStatedByPolicy() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withNoChargeAmountItem()
        .doNotChargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    performActionThatRequiresRefund(ClockUtil.getZonedDateTime().plusMonths(2));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(processingFee)));
    assertThat(loan, hasNoOverdueFine());
  }

  @Test
  void shouldNotChargeOverdueFineWhenLostFeeIsNotCancelled() {
    final double itemFee = 11.55;

    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    performActionThatRequiresRefund(ClockUtil.getZonedDateTime().plusMinutes(2));

    assertThat(loan, hasLostItemFee(isOpen(itemFee)));
    assertThat(loan, hasNoOverdueFine());
  }

  @Test
  void shouldNotChargeOverdueFineWhenProcessingFeeIsNotRefundable() {
    final double processingFee = 14.37;

    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withNoChargeAmountItem()
        .doNotRefundProcessingFeeWhenReturned()
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemProcessingFee(isOpen(processingFee)));
    assertThat(loan, hasNoOverdueFine());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void shouldCancelActualCostLostItemFeeWhenRefundPeriodWasNotExceeded(double processingFeeAmount) {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("test policy")
        .withActualCost(0.0)
        .chargeProcessingFeeWhenDeclaredLost(processingFeeAmount)
        .withFeeRefundInterval(Period.months(6))).getId());

    declareItemLost();

    List<JsonObject> actualCostRecordsBeforeAction = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsBeforeAction, hasSize(1));
    JsonObject recordBeforeAction = actualCostRecordsBeforeAction.get(0);
    assertThat(recordBeforeAction, isInStatus(OPEN));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());

    performActionThatRequiresRefund();

    UUID actualCostRecordId = UUID.fromString(recordBeforeAction.getString("id"));
    JsonObject recordAfterAction = actualCostRecordsClient.get(actualCostRecordId).getJson();
    assertThat(recordAfterAction, isInStatus(CANCELLED));
    assertThat(recordAfterAction, hasAdditionalInfoForStaff(actualCostFeeCancellationReason));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isClosedCancelled(processingFeeAmount))
      : hasNoLostItemProcessingFee());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0.0, 1.0})
  void shouldNotCancelActualCostLostItemFeeWhenRefundPeriodWasExceeded(double processingFeeAmount) {
    final int refundPeriodDurationDays = 3;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("test policy")
        .withActualCost(0.0)
        .chargeProcessingFeeWhenDeclaredLost(processingFeeAmount)
        .withFeeRefundInterval(Period.days(refundPeriodDurationDays))).getId());

    declareItemLost();

    List<JsonObject> actualCostRecordsBeforeAction = actualCostRecordsClient.getAll();
    assertThat(actualCostRecordsBeforeAction, hasSize(1));
    JsonObject recordBeforeAction = actualCostRecordsBeforeAction.get(0);
    assertThat(recordBeforeAction, isInStatus(OPEN));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());

    performActionThatRequiresRefund(
      getZonedDateTime().plusDays(refundPeriodDurationDays).plusMinutes(1));

    UUID actualCostRecordId = UUID.fromString(recordBeforeAction.getString("id"));
    JsonObject recordAfterAction = actualCostRecordsClient.get(actualCostRecordId).getJson();
    assertThat(recordAfterAction, isInStatus(OPEN));
    assertThat(loan, processingFeeAmount > 0
      ? hasLostItemProcessingFee(isOpen(processingFeeAmount))
      : hasNoLostItemProcessingFee());
  }

  protected void resolveLostItemFee() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
  }

  protected IndividualResource declareItemLost() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    loan = loansFixture.getLoanById(loan.getId());
    return loan;
  }

  protected void declareItemLost(double setCostFee) {
    useChargeableRefundableLostItemFee(setCostFee, 0.0);

    declareItemLost();
  }

  protected void useChargeableRefundableLostItemFee(double itemFee, double processingFee) {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName(format("Test lost policy processing fee %s, item fee %s",
          itemFee, processingFee))
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)).getId());
  }

  protected void declareItemLostWithActualCost(double itemFee, double processingFee) {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(lostItemFeePoliciesFixture
      .facultyStandardPolicy()
      .withName(format("Test lost policy processing fee %s, item fee %s",
        processingFee, itemFee))
      .chargeProcessingFeeWhenDeclaredLost(processingFee)
      .withActualCost(itemFee).refundFeesWithinMinutes(1)).getId());

    declareItemLost();
    String recordId = actualCostRecordsClient.getAll().get(0).getString("id");
    createLostItemFeeActualCostAccount(itemFee, UUID.fromString(recordId));
  }

  protected Matcher<JsonObject> isClosedCancelled(double amount) {
    return AccountMatchers.isClosedCancelled(cancellationReason, amount);
  }

  protected void createLostItemFeeActualCostAccount(double amount, UUID recordId) {
    createLostItemFeeActualCostAccount(amount, recordId, "default info for staff",
      "default info for patron");
  }

  protected void createLostItemFeeActualCostAccount(double amount, UUID recordId,
    String infoForStaff, String infoForPatron) {

    var account = feeFineAccountFixture.createLostItemFeeActualCostAccount(amount, loan,
      feeFineTypeFixture.lostItemActualCostFee(), feeFineOwnerFixture.cd1Owner(),
      infoForStaff, infoForPatron);

    JsonObject actualCostRecord = actualCostRecordsClient.getById(recordId).getJson();
    actualCostRecord.getJsonObject("feeFine").put("accountId", account.getId());
    actualCostRecordsClient.replace(recordId, actualCostRecord);
  }

  protected void runWithTimeOffset(Runnable runnable, Duration offset) {
    final Clock original = getClock();
    try {
      setClock(Clock.offset(original, offset));
      runnable.run();
    } finally {
      setClock(original);
    }
  }
}

