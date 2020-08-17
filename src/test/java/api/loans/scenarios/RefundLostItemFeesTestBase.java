package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.AccountActionsMatchers.CANCELLED_ITEM_RETURNED;
import static api.support.matchers.AccountActionsMatchers.CREDITED_FULLY;
import static api.support.matchers.AccountActionsMatchers.CREDITED_TO_BURSAR;
import static api.support.matchers.AccountActionsMatchers.CREDITED_TO_PATRON;
import static api.support.matchers.AccountActionsMatchers.REFUNDED_FULLY;
import static api.support.matchers.AccountActionsMatchers.REFUNDED_TO_BURSAR;
import static api.support.matchers.AccountActionsMatchers.REFUNDED_TO_PATRON;
import static api.support.matchers.AccountActionsMatchers.arePaymentRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.areTransferRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.isCancelledItemReturnedActionCreated;
import static api.support.matchers.AccountMatchers.isClosedCancelledItemReturned;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeActions;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeActions;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static api.support.matchers.LoanMatchers.isClosed;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTime.parse;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.http.client.IndividualResource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public abstract class RefundLostItemFeesTestBase extends APITests {
  private static final String DATE_ACTION_PROPERTY = "dateAction";

  protected final IndividualResource item = itemsFixture.basedUponNod();
  protected IndividualResource loan;

  protected abstract void performActionThatRequiresRefund();

  protected abstract void performActionThatRequiresRefund(DateTime actionDate);

  @Before
  public void activateChargeableLostItemFeePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());
  }

  @Test
  public void shouldCancelItemFeeOnlyWhenNoOtherFeesCharged() {
    final double itemFee = 15.00;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFee()
        .withSetCost(itemFee)).getId());

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelledItemReturned(itemFee)));
    assertThat(loan, hasLostItemFeeActions(isCancelledItemReturnedActionCreated(itemFee)));
  }

  @Test
  public void shouldCancelItemProcessingFeeOnlyWhenNoOtherFeesChargedAndNoPaymentsMade() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldNotRefundProcessingFeeWhenPolicyStatesNotTo() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
        .doNotRefundProcessingFeeWhenReturned()
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      not(arePaymentRefundActionsCreated(processingFee))));
  }

  @Test
  public void shouldNotRefundFeesWhenReturnedAfterRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    mockClockManagerToReturnFixedDateTime(now(DateTimeZone.UTC).plusMinutes(2));

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isTransferredFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(
      not(areTransferRefundActionsCreated(setCostFee))));

    assertThat(loan, hasLostItemProcessingFee(isPaidFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      not(arePaymentRefundActionsCreated(processingFee))));
  }

  @Test
  public void shouldRefundFeesAtAnyPointWhenNoMaximumRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
        .withSetCost(setCostFee)
        .withNoFeeRefundInterval()).getId());

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(areTransferRefundActionsCreated(setCostFee)));

    assertThat(loan, hasLostItemProcessingFee(isRefundedFully(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      arePaymentRefundActionsCreated(processingFee)));
  }

  @Test
  public void shouldCancelBothItemAndProcessingFeesWhenNeitherHaveBeenPaid() {
    final double processingFee = 5.0;
    final double itemFee = 10.0;

    declareItemLost();

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isClosedCancelledItemReturned(itemFee)));
    assertThat(loan, hasLostItemFeeActions(isCancelledItemReturnedActionCreated(itemFee)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldRefundTransferredFee() {
    final double setCostFee = 10.89;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(areTransferRefundActionsCreated(setCostFee)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldRefundPaidFee() {
    final double setCostFee = 9.99;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.payLostItemFee(loan.getId());

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(arePaymentRefundActionsCreated(setCostFee)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldRefundPaidAndTransferredFee() {
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
    assertThat(loan, hasLostItemFeeActions(allOf(
      areTransferRefundActionsCreated(transferAmount),
      arePaymentRefundActionsCreated(paymentAmount)
    )));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldRefundPartiallyPaidAndTransferredFeeAndCancelRemainingAmount() {
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

    assertThat(loan, hasLostItemFee(isClosedCancelledItemReturned(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(allOf(
      isCancelledItemReturnedActionCreated(remainingAmount),
      areTransferRefundActionsCreated(remainingAmount, transferAmount),
      arePaymentRefundActionsCreated(remainingAmount, paymentAmount)
    )));
    lostItemFeeActionsOrderedHistorically();

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));
  }

  @Test
  public void shouldChargeOverdueFineWhenStatedByPolicyAndLostFeesCanceled() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
        .withNoChargeAmountItem()
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(now().plusMonths(2)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));

    assertThat(loan, hasOverdueFine());
  }

  @Test
  public void shouldNotChargeOverdueFineWhenNotStatedByPolicy() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
        .withNoChargeAmountItem()
        .doNotChargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    performActionThatRequiresRefund(now().plusMonths(2));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));

    assertThat(loan, hasNoOverdueFine());
  }

  @Test
  public void shouldNotChargeOverdueFineWhenLostFeeIsNotCancelled() {
    final double itemFee = 11.55;

    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFee()
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    mockClockManagerToReturnFixedDateTime(DateTime.now(DateTimeZone.UTC).plusMinutes(2));

    performActionThatRequiresRefund();

    assertThat(loan, hasLostItemFee(isOpen(itemFee)));
    assertThat(loan, hasNoOverdueFine());
  }

  @Test
  public void shouldNotChargeOverdueFineWhenProcessingFeeIsNotRefundable() {
    final double processingFee = 14.37;

    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee(processingFee)
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
        .chargeProcessingFee(processingFee)
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)).getId());
  }

  private JsonObject getLostItemFeeAccountForLoan() {
    return accountsClient.getMany(queryFromTemplate(
      "loanId==%s and feeFineType==\"Lost item fee\"", loan.getId())).getFirst();
  }

  private MultipleJsonRecords getAccountActions(String accountId) {
    return feeFineActionsClient.getMany(queryFromTemplate("accountId==%s", accountId));
  }

  @SuppressWarnings("unchecked")
  private void lostItemFeeActionsOrderedHistorically() {
    final JsonObject fee = getLostItemFeeAccountForLoan();
    final List<JsonObject> accountsOrdered = getAccountActions(fee.getString("id"))
      .stream()
      .sorted((first, second) -> parse(second.getString(DATE_ACTION_PROPERTY))
        .compareTo(parse(first.getString(DATE_ACTION_PROPERTY))))
      .collect(Collectors.toList());

    assertThat(accountsOrdered, containsInRelativeOrder(
      allOf(
        hasJsonPath("typeAction", CANCELLED_ITEM_RETURNED),
        hasJsonPath("transactionInformation", "-")),
      allOf(
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUNDED_TO_PATRON)),
      allOf(
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", CREDITED_TO_PATRON)),
      allOf(
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUNDED_TO_BURSAR)),
      allOf(
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", CREDITED_TO_BURSAR))
    ));

    final int numberOfRefundCancelActions = 5;
    for (int index = 1; index < numberOfRefundCancelActions; index++) {
      final JsonObject previousAction = accountsOrdered.get(index - 1);
      final JsonObject currentAction = accountsOrdered.get(index);

      assertThat(parse(previousAction.getString(DATE_ACTION_PROPERTY)),
        greaterThan(parse(currentAction.getString(DATE_ACTION_PROPERTY))));
    }
  }
}
