package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.iterableWithSize;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import api.support.APITests;
import api.support.MultipleJsonRecords;
import api.support.builders.DeclareItemLostRequestBuilder;
import io.vertx.core.json.JsonObject;

public class CheckInDeclaredLostItemTest extends APITests {
  private static final String LOST_ITEM_FEE = "Lost item fee";
  private static final String LOST_ITEM_PROCESSING_FEE = "Lost item processing fee";
  private static final String CANCELLED_ITEM_RETURNED = "Cancelled item returned";

  private IndividualResource item;
  private IndividualResource loan;

  @Before
  public void activateChargeableLostItemFeePolicy() {
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());
  }

  @Test
  public void canCancelItemFeeOnly() {
    final double itemFee = 15.00;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFee()
        .withSetCost(itemFee)).getId());

    declareItemLost();

    verifyFeesAssigned(notNullValue(JsonObject.class), nullValue(JsonObject.class));

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemFee(allOf(
      hasJsonPath("amount", itemFee),
      hasJsonPath("remaining", 0.00),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemFeeAction(hasItems(allOf(
      hasJsonPath("amountAction", itemFee),
      hasJsonPath("balance", 0.00),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));
  }

  @Test
  public void canCancelProcessingFeeOnly() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    verifyFeesAssigned(nullValue(JsonObject.class), notNullValue(JsonObject.class));

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", processingFee),
      hasJsonPath("remaining", 0.00),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemProcessingFeeAction(hasItems(allOf(
      hasJsonPath("amountAction", processingFee),
      hasJsonPath("balance", 0.00),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));
  }

  @Test
  public void processingFeeIsNotRefundedWhenDisabledInPolicy() {
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .doNotRefundProcessingFeeWhenReturned()
        .withNoChargeAmountItem()).getId());

    declareItemLost();

    verifyFeesAssigned(nullValue(JsonObject.class), notNullValue(JsonObject.class));

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", processingFee),
      hasJsonPath("remaining", processingFee),
      hasJsonPath("status.name", "Open")
    ));
    verifyLostItemProcessingFeeAction(allOf(
      iterableWithSize(1),
      hasItem(allOf(
        hasJsonPath("amountAction", processingFee),
        hasJsonPath("balance", processingFee),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("createdAt", "Circ Desk 1"),
        hasNoJsonPath("paymentStatus.name")
      ))));
  }

  @Test
  public void feeIsNotRefundedIfRefundPeriodExceeded() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(processingFee)
        .withSetCost(setCostFee)
        .refundFeesWithinMinutes(1)).getId());

    declareItemLost();

    verifyFeesAssigned(notNullValue(JsonObject.class), notNullValue(JsonObject.class));

    mockClockManagerToReturnFixedDateTime(DateTime.now(DateTimeZone.UTC).plusMinutes(2));

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemFee(allOf(
      hasJsonPath("amount", setCostFee),
      hasJsonPath("remaining", setCostFee),
      hasJsonPath("status.name", "Open")
    ));
    verifyLostItemFeeAction(allOf(
      iterableWithSize(1),
      hasItems(allOf(
        hasJsonPath("amountAction", setCostFee),
        hasJsonPath("balance", setCostFee),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("createdAt", "Circ Desk 1"),
        hasNoJsonPath("paymentStatus.name")
      ))));

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", processingFee),
      hasJsonPath("remaining", processingFee),
      hasJsonPath("status.name", "Open")
    ));
    verifyLostItemProcessingFeeAction(allOf(
      iterableWithSize(1),
      hasItems(allOf(
        hasJsonPath("amountAction", processingFee),
        hasJsonPath("balance", processingFee),
        hasJsonPath("source", "Admin, Admin"),
        hasJsonPath("createdAt", "Circ Desk 1"),
        hasNoJsonPath("paymentStatus.name")
      ))));
  }

  @Test
  public void canCancelItemAndProcessingFees() {
    declareItemLost();

    verifyFeesAssigned(notNullValue(JsonObject.class), notNullValue(JsonObject.class));

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemFee(allOf(
      hasJsonPath("amount", 10.00),
      hasJsonPath("remaining", 0.00),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemFeeAction(hasItems(allOf(
      hasJsonPath("amountAction", 10.00),
      hasJsonPath("balance", 0.00),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", 5.00),
      hasJsonPath("remaining", 0.00),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemProcessingFeeAction(hasItems(allOf(
      hasJsonPath("amountAction", 5.00),
      hasJsonPath("balance", 0.00),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));
  }

  private JsonObject getAccountForLoan(UUID loanId, String type) {
    return accountsClient.getMany(queryFromTemplate(
      "loanId==%s and feeFineType==\"%s\"", loanId.toString(), type)).getFirst();
  }

  private MultipleJsonRecords getAccountActions(String accountId) {
    return feeFineActionsClient.getMany(queryFromTemplate("accountId==%s", accountId));
  }

  private void verifyFeesAssigned(Matcher<JsonObject> itemFeeMatcher,
    Matcher<JsonObject> processingFeeMatcher) {

    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_FEE), itemFeeMatcher);
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), processingFeeMatcher);
  }

  private void verifyLostItemFee(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_FEE), matcher);
  }

  private void verifyLostItemFeeAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_FEE, actionMatcher);
  }

  private void verifyLostItemProcessingFee(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), matcher);
  }

  private void verifyLostItemProcessingFeeAction(Matcher<Iterable<JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_PROCESSING_FEE, actionMatcher);
  }

  private void verifyFeeAction(String feeType, Matcher<Iterable<JsonObject>> actionMatcher) {
    final JsonObject fee = getAccountForLoan(loan.getId(), feeType);

    assertThat(fee, notNullValue());
    assertThat(getAccountActions(fee.getString("id")), actionMatcher);
  }

  private void declareItemLost() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.charlotte());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointsFixture.cd1().getId())
      .forLoanId(loan.getId()));

    loan = loansFixture.getLoanById(loan.getId());
  }
}
