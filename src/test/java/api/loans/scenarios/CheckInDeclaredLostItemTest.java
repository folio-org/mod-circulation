package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.ValidationErrorMatchers.hasErrorWith;
import static api.support.matchers.ValidationErrorMatchers.hasMessage;
import static api.support.matchers.ValidationErrorMatchers.hasParameter;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.greaterThan;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTime.parse;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.support.http.client.IndividualResource;
import org.folio.circulation.support.http.client.Response;
import org.hamcrest.Matcher;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInDeclaredLostItemTest extends APITests {
  private static final String LOST_ITEM_FEE = "Lost item fee";
  private static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";
  private static final String CANCELLED_ITEM_RETURNED = "Cancelled item returned";
  private static final String REFUNDED_FULLY = "Refunded fully";
  private static final String CREDITED_FULLY = "Credited fully";
  private static final String LOST_ITEM_FOUND = "Lost item found";
  private static final String REFUND_TO_BURSAR = "Refund to Bursar";
  private static final String REFUND_TO_PATRON = "Refund to patron";
  private static final String DATE_ACTION = "dateAction";

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

    verifyLostItemFeeAccount(isAccountClosed(itemFee));
    verifyLostItemFeeAccountAction(isCloseActionCreated(itemFee));
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

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
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

    verifyLostItemProcessingFeeAccount(allOf(
      hasJsonPath("amount", processingFee),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Paid fully")));
    verifyLostItemProcessingFeeAccountAction(
      not(arePaymentRefundActionsCreated(processingFee)));
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

    verifyLostItemFeeAccount(allOf(
      hasJsonPath("amount", setCostFee),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Transferred fully")));
    verifyLostItemFeeAccountAction(
      not(areTransferRefundActionsCreated(setCostFee)));

    verifyLostItemProcessingFeeAccount(allOf(
      hasJsonPath("amount", processingFee),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", "Paid fully")));
    verifyLostItemProcessingFeeAccountAction(
      not(arePaymentRefundActionsCreated(processingFee)));
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

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(setCostFee));

    verifyLostItemProcessingFeeAccount(isAccountRefundedFully(processingFee));
    verifyLostItemProcessingFeeAccountAction(arePaymentRefundActionsCreated(processingFee));
  }

  @Test
  public void shouldCancelBothItemAndProcessingFeesWhenNeitherHaveBeenPaid() {
    final double processingFee = 5.0;
    final double itemFee = 10.0;

    declareItemLost();

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountClosed(itemFee));
    verifyLostItemFeeAccountAction(isCloseActionCreated(itemFee));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
  }

  @Test
  public void shouldRefundTransferredFee() {
    final double setCostFee = 10.89;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.transferLostItemFee(loan.getId());

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(setCostFee));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
  }

  @Test
  public void shouldRefundPaidFee() {
    final double setCostFee = 9.99;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    feeFineAccountFixture.payLostItemFee(loan.getId());

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(setCostFee));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
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

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(transferAmount));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(paymentAmount));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
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

    verifyLostItemFeeAccount(isAccountClosed(setCostFee));
    verifyLostItemFeeAccountAction(isCloseActionCreated(remainingAmount));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(remainingAmount, transferAmount));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(remainingAmount, paymentAmount));
    lostItemFeeActionsHasDateActionAscending();

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
  }

  @Test
  public void shouldChargeOverdueFineWhenStatedByPolicy() {
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

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));

    assertThat(getAccountForLoan(loan.getId(), "Overdue fine"), notNullValue());
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

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));

    assertThat(getAccountForLoan(loan.getId(), "Overdue fine"), nullValue());
  }

  @Test
  public void shouldRefundPaidAmountForLostAndPaidItem() {
    final double setCostFee = 10.00;

    declareItemLostAndPayFees(setCostFee);

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(setCostFee));
  }

  @Test
  public void shouldRefundOnlyLastLoanForLostAndPaidItem() {
    final double firstFee = 20.00;
    final double secondFee = 30.00;

    useChargeableRefundableLostItemFee(firstFee, 0.0);

    final UUID firstLoanId = declareItemLost();
    mockClockManagerToReturnFixedDateTime(now(DateTimeZone.UTC).plusMinutes(2));
    // Item fee won't be cancelled, because refund period is exceeded
    checkInFixture.checkInByBarcode(item);
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    final UUID secondLoanId = declareItemLostAndPayFees(secondFee);
    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(firstLoanId, allOf(
      hasJsonPath("status.name", "Open"),
      hasJsonPath("remaining", firstFee),
      hasJsonPath("amount", firstFee)));
    verifyLostItemFeeAccountAction(firstLoanId, not(isCloseActionCreated(firstFee)));

    verifyLostItemFeeAccount(secondLoanId, isAccountRefundedFully(secondFee));
    verifyLostItemFeeAccountAction(secondLoanId, arePaymentRefundActionsCreated(secondFee));
  }

  @Test
  public void shouldFailIfNoLoanForLostAndPaidItem() {
    final double setCost = 20.00;

    declareItemLostAndPayFees(setCost);

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

  private UUID declareItemLostAndPayFees(double setCostFeeAmount) {
    useChargeableRefundableLostItemFee(setCostFeeAmount, 0);

    final UUID loanId = declareItemLost();

    feeFineAccountFixture.payLostItemFee(loanId);
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loanId);

    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
    assertThat(loansFixture.getLoanById(loanId).getJson(), isClosed());

    return loanId;
  }

  private JsonObject getAccountForLoan(UUID loanId, String type) {
    return accountsClient.getMany(queryFromTemplate(
      "loanId==%s and feeFineType==\"%s\"", loanId.toString(), type)).getFirst();
  }

  private MultipleJsonRecords getAccountActions(String accountId) {
    return feeFineActionsClient.getMany(queryFromTemplate("accountId==%s", accountId));
  }

  private void verifyLostItemFeeAccount(Matcher<JsonObject> matcher) {
    verifyLostItemFeeAccount(loan.getId(), matcher);
  }

  private void verifyLostItemFeeAccount(UUID loanId, Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loanId, LOST_ITEM_FEE), matcher);
  }

  private void verifyLostItemFeeAccountAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyLostItemFeeAccountAction(loan.getId(), actionMatcher);
  }

  private void verifyLostItemFeeAccountAction(UUID loanId, Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(loanId, LOST_ITEM_FEE, actionMatcher);
  }

  private void verifyLostItemProcessingFeeAccount(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), matcher);
  }

  private void verifyLostItemProcessingFeeAccountAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_PROCESSING_FEE, actionMatcher);
  }

  private void verifyFeeAction(String feeType, Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(loan.getId(), feeType, actionMatcher);
  }

  private void verifyFeeAction(UUID loanId, String feeType, Matcher<Iterable<JsonObject>> actionMatcher) {
    final JsonObject fee = getAccountForLoan(loanId, feeType);

    assertThat(fee, notNullValue());

    final MultipleJsonRecords accounts = getAccountActions(fee.getString("id"));
    assertThat(accounts, actionMatcher);
    assertThat(accounts, everyItem(allOf(
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", startsWith("Circ Desk"))
    )));
  }

  private UUID declareItemLost() {
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    loan = loansFixture.getLoanById(loan.getId());
    return loan.getId();
  }

  private Matcher<Iterable<JsonObject>> areTransferRefundActionsCreated(double transferAmount) {
    return areTransferRefundActionsCreated(0.0, transferAmount);
  }

  private Matcher<Iterable<JsonObject>> areTransferRefundActionsCreated(
    double remaining, double transferAmount) {

    final FeeAmount creditAmount = new FeeAmount(remaining)
      .subtract(new FeeAmount(transferAmount));
    return hasItems(
      allOf(
        hasJsonPath("amountAction", transferAmount),
        hasJsonPath("balance", creditAmount.toDouble()),
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("amountAction", transferAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_BURSAR),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND))
    );
  }

  private Matcher<Iterable<JsonObject>> arePaymentRefundActionsCreated(double paymentAmount) {
    return arePaymentRefundActionsCreated(0.0, paymentAmount);
  }

  private Matcher<Iterable<JsonObject>> arePaymentRefundActionsCreated(
    double remaining, double paymentAmount) {

    final FeeAmount creditAmount = new FeeAmount(remaining)
      .subtract(new FeeAmount(paymentAmount));
    return hasItems(
      allOf(
        hasJsonPath("amountAction", paymentAmount),
        hasJsonPath("balance", creditAmount.toDouble()),
        hasJsonPath("typeAction", CREDITED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_PATRON),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("amountAction", paymentAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", REFUND_TO_PATRON),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND))
    );
  }

  private Matcher<Iterable<JsonObject>> isCloseActionCreated(double amount) {
    return hasItems(allOf(
      hasJsonPath("amountAction", amount),
      hasJsonPath("balance", 0.0),
      hasJsonPath("typeAction", CANCELLED_ITEM_RETURNED))
    );
  }

  private Matcher<JsonObject> isAccountRefundedFully(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", REFUNDED_FULLY));
  }

  private Matcher<JsonObject> isAccountClosed(double amount) {
    return allOf(
      hasJsonPath("amount", amount),
      hasJsonPath("remaining", 0.0),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED));
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

  @SuppressWarnings("unchecked")
  private void lostItemFeeActionsHasDateActionAscending() {
    final JsonObject fee = getAccountForLoan(loan.getId(), LOST_ITEM_FEE);
    final List<JsonObject> accountsOrdered = getAccountActions(fee.getString("id"))
      .stream()
      .sorted((first, second) -> parse(second.getString(DATE_ACTION))
        .compareTo(parse(first.getString(DATE_ACTION))))
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
    for (int i = 1; i < numberOfRefundCancelActions; i++) {
      final JsonObject previousAction = accountsOrdered.get(i - 1);
      final JsonObject currentAction = accountsOrdered.get(i);

      assertThat(parse(previousAction.getString(DATE_ACTION)),
        greaterThan(parse(currentAction.getString(DATE_ACTION))));
    }
  }
}
