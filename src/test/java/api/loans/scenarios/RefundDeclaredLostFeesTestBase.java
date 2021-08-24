package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static api.support.matchers.LoanMatchers.isClosed;
import static org.hamcrest.MatcherAssert.assertThat;

import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.http.IndividualResource;
import api.support.matchers.AccountMatchers;
import api.support.spring.SpringApiTest;
import io.vertx.core.json.JsonObject;

public abstract class RefundDeclaredLostFeesTestBase extends SpringApiTest {
  protected final IndividualResource item = itemsFixture.basedUponNod();
  private final String cancellationReason;
  protected IndividualResource loan;

  public RefundDeclaredLostFeesTestBase(String cancellationReason) {
    this.cancellationReason = cancellationReason;
  }

  protected void performActionThatRequiresRefund() {
    performActionThatRequiresRefund(ClockUtil.getDateTime());
  }

  protected abstract void performActionThatRequiresRefund(DateTime actionDate);

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

    performActionThatRequiresRefund(ClockUtil.getDateTime().plusMinutes(2));

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

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isRefundedFully(processingFee)));
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

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
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

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
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

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
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

    performActionThatRequiresRefund(ClockUtil.getDateTime().plusMonths(2));

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

    performActionThatRequiresRefund(ClockUtil.getDateTime().plusMonths(2));

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

    performActionThatRequiresRefund(ClockUtil.getDateTime().plusMinutes(2));

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
        .withName(String.format("Test lost policy processing fee %s, item fee %s",
          itemFee, processingFee))
        .chargeProcessingFeeWhenDeclaredLost(processingFee)
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)).getId());
  }

  private Matcher<JsonObject> isClosedCancelled(double amount) {
    return AccountMatchers.isClosedCancelled(cancellationReason, amount);
  }
}
