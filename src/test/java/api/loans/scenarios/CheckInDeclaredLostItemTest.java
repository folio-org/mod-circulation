package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.everyItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.domain.FeeAmount;
import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
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
  private static final String LOST_ITEM_FEE = "Lost item fee";
  private static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";
  private static final String CANCELLED_ITEM_RETURNED = "Cancelled item returned";
  private static final String REFUNDED_FULLY = "Refunded fully";
  private static final String CREDITED_FULLY = "Credited fully";
  private static final String LOST_ITEM_FOUND = "Lost item found";

  private IndividualResource item;
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
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
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
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .doNotRefundProcessingFeeWhenReturned()
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    feeFineAccountFixture.pay(feeFineAccountFixture
      .getLostItemProcessingFeeAccount(loan.getId()), processingFee);

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

    feeFineAccountFixture.transfer(feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId()), setCostFee);
    feeFineAccountFixture.pay(feeFineAccountFixture
      .getLostItemProcessingFeeAccount(loan.getId()), processingFee);

    mockClockManagerToReturnFixedDateTime(DateTime.now(DateTimeZone.UTC).plusMinutes(2));

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
  public void shouldRefundFeesWhenNoMaximumRefundPeriod() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withSetCost(setCostFee)
        .withNoFeeRefundInterval()).getId());

    declareItemLost();

    feeFineAccountFixture.transfer(feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId()), setCostFee);
    feeFineAccountFixture.pay(feeFineAccountFixture
      .getLostItemProcessingFeeAccount(loan.getId()), processingFee);

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

    final JsonObject lostItemFeeAccount = feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId());
    feeFineAccountFixture.transfer(lostItemFeeAccount.getString("id"), setCostFee);

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

    final JsonObject lostItemFeeAccount = feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId());
    feeFineAccountFixture.pay(lostItemFeeAccount.getString("id"), setCostFee);

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

    final JsonObject lostItemFeeAccount = feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId());
    feeFineAccountFixture.transfer(lostItemFeeAccount, transferAmount);
    feeFineAccountFixture.pay(lostItemFeeAccount, paymentAmount);

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountRefundedFully(setCostFee));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(transferAmount));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(paymentAmount));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
  }

  @Test
  public void shouldCloseAndRefundPaidAndTransferredFee() {
    final double transferAmount = 6.0;
    final double paymentAmount = 4.0;
    final double remainingAmount = 5.99;
    final double setCostFee = transferAmount + paymentAmount + remainingAmount;
    final double processingFee = 5.00;

    useChargeableRefundableLostItemFee(setCostFee, processingFee);

    declareItemLost();

    final JsonObject lostItemFeeAccount = feeFineAccountFixture
      .getLostItemFeeAccount(loan.getId());
    feeFineAccountFixture.transfer(lostItemFeeAccount, transferAmount);
    feeFineAccountFixture.pay(lostItemFeeAccount, paymentAmount);

    checkInFixture.checkInByBarcode(item);

    verifyLostItemFeeAccount(isAccountClosed(setCostFee));
    verifyLostItemFeeAccountAction(isCloseActionCreated(remainingAmount));
    verifyLostItemFeeAccountAction(areTransferRefundActionsCreated(remainingAmount, transferAmount));
    verifyLostItemFeeAccountAction(arePaymentRefundActionsCreated(remainingAmount, paymentAmount));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));
  }

  @Test
  public void shouldChargeOverdueFineWhenAllowedByPolicy() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withNoChargeAmountItem()
        .chargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(DateTime.now().plusMonths(2)));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));

    assertThat(getAccountForLoan(loan.getId(), "Overdue fine"), notNullValue());
  }

  @Test
  public void shouldNotChargeOverdueFineWhenDisallowedByPolicy() {
    final double processingFee = 12.99;

    // Create overdue fine type
    feeFineTypeFixture.overdueFine();

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withNoChargeAmountItem()
        .doNotChargeOverdueFineWhenReturned()).getId());

    declareItemLost();

    checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(item)
      .at(servicePointsFixture.cd1())
      .on(DateTime.now().plusMonths(2)));

    verifyLostItemProcessingFeeAccount(isAccountClosed(processingFee));
    verifyLostItemProcessingFeeAccountAction(isCloseActionCreated(processingFee));

    assertThat(getAccountForLoan(loan.getId(), "Overdue fine"), nullValue());
  }

  private JsonObject getAccountForLoan(UUID loanId, String type) {
    return accountsClient.getMany(queryFromTemplate(
      "loanId==%s and feeFineType==\"%s\"", loanId.toString(), type)).getFirst();
  }

  private MultipleJsonRecords getAccountActions(String accountId) {
    return feeFineActionsClient.getMany(queryFromTemplate("accountId==%s", accountId));
  }

  private void verifyLostItemFeeAccount(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_FEE), matcher);
  }

  private void verifyLostItemFeeAccountAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_FEE, actionMatcher);
  }

  private void verifyLostItemProcessingFeeAccount(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), matcher);
  }

  private void verifyLostItemProcessingFeeAccountAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_PROCESSING_FEE, actionMatcher);
  }

  private void verifyFeeAction(String feeType, Matcher<Iterable<JsonObject>> actionMatcher) {
    final JsonObject fee = getAccountForLoan(loan.getId(), feeType);

    assertThat(fee, notNullValue());

    final MultipleJsonRecords accounts = getAccountActions(fee.getString("id"));
    assertThat(accounts, actionMatcher);
    assertThat(accounts, everyItem(allOf(
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", startsWith("Circ Desk"))
    )));
  }

  private void declareItemLost() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    loan = loansFixture.getLoanById(loan.getId());
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
        hasJsonPath("transactionInformation", "Refund to Bursar"),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("amountAction", transferAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", "Refund to Bursar"),
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
        hasJsonPath("transactionInformation", "Refund to patron"),
        hasJsonPath("paymentMethod", LOST_ITEM_FOUND)),
      allOf(
        hasJsonPath("amountAction", paymentAmount),
        hasJsonPath("balance", remaining),
        hasJsonPath("typeAction", REFUNDED_FULLY),
        hasJsonPath("transactionInformation", "Refund to patron"),
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
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withSetCost(itemFee)
        .refundFeesWithinMinutes(1)).getId());
  }
}
