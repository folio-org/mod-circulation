package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.AccountActionsMatchers.CANCELLED_ITEM_RETURNED;
import static api.support.matchers.AccountActionsMatchers.CREDITED_FULLY;
import static api.support.matchers.AccountActionsMatchers.REFUNDED_FULLY;
import static api.support.matchers.AccountActionsMatchers.REFUND_TO_BURSAR;
import static api.support.matchers.AccountActionsMatchers.REFUND_TO_PATRON;
import static api.support.matchers.AccountActionsMatchers.arePaymentRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.areTransferRefundActionsCreated;
import static api.support.matchers.AccountActionsMatchers.isCancelledItemReturnedActionCreated;
import static api.support.matchers.AccountMatchers.isClosedCancelledItemReturned;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.AccountMatchers.isPaidFully;
import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.AccountMatchers.isTransferredFully;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemFeeActions;
import static api.support.matchers.LoanAccountActionsMatcher.hasLostItemProcessingFeeActions;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasNoOverdueFine;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTime.parse;

import java.util.List;
import java.util.stream.Collectors;

import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInDeclaredLostItemTest extends APITests {
  private static final String DATE_ACTION_PROPERTY = "dateAction";

  private final IndividualResource item = itemsFixture.basedUponNod();
  private IndividualResource loan;

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(now().plusMonths(2)));

    assertThat(loan, hasLostItemProcessingFee(isClosedCancelledItemReturned(processingFee)));
    assertThat(loan, hasLostItemProcessingFeeActions(
      isCancelledItemReturnedActionCreated(processingFee)));

    assertThat(loan, hasNoOverdueFine());
  }

  @Test
  public void shouldRefundPaidAmountForLostAndPaidItem() {
    final double setCostFee = 10.00;

    declareItemLost(setCostFee);
    resolveLostItemFee();

    checkInFixture.checkInByBarcode(item);

    assertThat(loan, hasLostItemFee(isRefundedFully(setCostFee)));
    assertThat(loan, hasLostItemFeeActions(arePaymentRefundActionsCreated(setCostFee)));
  }

  @Test
  public void shouldRefundOnlyLastLoanForLostAndPaidItem() {
    final double firstFee = 20.00;
    final double secondFee = 30.00;

    useChargeableRefundableLostItemFee(firstFee, 0.0);

    final IndividualResource firstLoan = declareItemLost();
    mockClockManagerToReturnFixedDateTime(now(DateTimeZone.UTC).plusMinutes(2));
    // Item fee won't be cancelled, because refund period is exceeded
    checkInFixture.checkInByBarcode(item);
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    declareItemLost(secondFee);
    resolveLostItemFee();
    checkInFixture.checkInByBarcode(item);

    assertThat(firstLoan, hasLostItemFee(isOpen(firstFee)));
    assertThat(firstLoan, hasLostItemFeeActions(
      not(isCancelledItemReturnedActionCreated(firstFee))));

    assertThat(loan, hasLostItemFee(isRefundedFully(secondFee)));
    assertThat(loan, hasLostItemFeeActions(arePaymentRefundActionsCreated(secondFee)));
  }

  @Test
  public void shouldFailIfNoLoanForLostAndPaidItem() {
    final double setCost = 20.00;

    declareItemLost(setCost);
    resolveLostItemFee();

    // Remove the loan from storage
    loansFixture.deleteLoan(loan.getId());

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Item is lost however there is no declared lost loan found"),
      hasParameter("itemId", item.getId().toString()))));
  }

  @Test
  public void shouldFailIfLastLoanIsNotDeclaredLostForLostAndPaidItem() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    checkInFixture.checkInByBarcode(item);

    // Update the item status in storage
    itemsClient.replace(item.getId(), itemsClient.get(item).getJson().copy()
      .put("status", new JsonObject().put("name", "Lost and paid")));

    final Response checkInResponse = checkInFixture.attemptCheckInByBarcode(
      new CheckInByBarcodeRequestBuilder()
        .at(servicePointsFixture.cd1())
        .forItem(item));

    assertThat(checkInResponse.getJson(), hasErrorWith(allOf(
      hasMessage("Last loan for lost item is not declared lost"),
      hasParameter("loanId", loan.getId().toString()))));
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

    checkInFixture.checkInByBarcode(item);

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

    checkInFixture.checkInByBarcode(item);

    assertThat(loan, hasLostItemProcessingFee(isOpen(processingFee)));
    assertThat(loan, hasNoOverdueFine());
  }

  private void resolveLostItemFee() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
  }

  private IndividualResource declareItemLost() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    loan = loansFixture.getLoanById(loan.getId());
    return loan;
  }

  private void declareItemLost(double setCostFee) {
    useChargeableRefundableLostItemFee(setCostFee, 0.0);

    declareItemLost();
  }

  private void useChargeableRefundableLostItemFee(double itemFee, double processingFee) {
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
        hasJsonPath("transactionInformation", REFUND_TO_PATRON)),
      allOf(
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_PATRON)),
      allOf(
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR)),
      allOf(
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR))
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
