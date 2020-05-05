package api.loans.scenarios;

import static api.support.http.CqlQuery.queryFromTemplate;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.UUID;

import org.folio.circulation.support.http.client.IndividualResource;
import org.hamcrest.Matcher;
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
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .doNotChargeProcessingFee()
        .withSetCost(15.0)
    ).getId());

    declareItemLost();

    verifyFeesAssigned(notNullValue(), nullValue());

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemFee(allOf(
      hasJsonPath("amount", "15.00"),
      hasJsonPath("remaining", "0.00"),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemFeeAction(hasItem(allOf(
      hasJsonPath("amountAction", "15.00"),
      hasJsonPath("balance", "0.00"),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));
  }

  @Test
  public void canCancelProcessingFeeOnly() {
    useLostItemPolicy(lostItemFeePoliciesFixture.create(
      lostItemFeePoliciesFixture.facultyStandardPolicy()
        .withName("Test check in")
        .chargeProcessingFee()
        .withLostItemProcessingFee(12.99)
        .withNoChargeAmountItem()
    ).getId());

    declareItemLost();

    verifyFeesAssigned(nullValue(), notNullValue());

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", "12.99"),
      hasJsonPath("remaining", "0.00"),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemProcessingFeeAction(hasItem(allOf(
      hasJsonPath("amountAction", "12.99"),
      hasJsonPath("balance", "0.00"),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));
  }

  @Test
  public void canCancelItemAndProcessingFees() {
    declareItemLost();

    verifyFeesAssigned(notNullValue(), notNullValue());

    checkInFixture.checkInByBarcode(item, servicePointsFixture.cd2().getId());

    verifyLostItemFee(allOf(
      hasJsonPath("amount", "10.00"),
      hasJsonPath("remaining", "0.00"),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemFeeAction(hasItem(allOf(
      hasJsonPath("amountAction", "10.00"),
      hasJsonPath("balance", "0.00"),
      hasJsonPath("source", "Admin, Admin"),
      hasJsonPath("createdAt", "Circ Desk 2")
    )));

    verifyLostItemProcessingFee(allOf(
      hasJsonPath("amount", "5.00"),
      hasJsonPath("remaining", "0.00"),
      hasJsonPath("status.name", "Closed"),
      hasJsonPath("paymentStatus.name", CANCELLED_ITEM_RETURNED)
    ));
    verifyLostItemProcessingFeeAction(hasItem(allOf(
      hasJsonPath("amountAction", "5.00"),
      hasJsonPath("balance", "0.00"),
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

  private void verifyFeesAssigned(Matcher<? super JsonObject> itemFeeMatcher,
                                  Matcher<? super JsonObject> processingFeeMatcher) {

    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_FEE), itemFeeMatcher);
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), processingFeeMatcher);
  }

  private void verifyLostItemFee(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_FEE), matcher);
  }

  private void verifyLostItemFeeAction(Matcher<Iterable<? super JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_FEE, actionMatcher);
  }

  private void verifyLostItemProcessingFee(Matcher<JsonObject> matcher) {
    assertThat(getAccountForLoan(loan.getId(), LOST_ITEM_PROCESSING_FEE), matcher);
  }

  private void verifyLostItemProcessingFeeAction(Matcher<Iterable<? super JsonObject>> actionMatcher) {
    verifyFeeAction(LOST_ITEM_PROCESSING_FEE, actionMatcher);
  }

  private void verifyFeeAction(String feeType, Matcher<Iterable<? super JsonObject>> actionMatcher) {
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
