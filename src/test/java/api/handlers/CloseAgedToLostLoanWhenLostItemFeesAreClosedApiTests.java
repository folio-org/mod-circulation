package api.handlers;

import static api.support.matchers.ItemMatchers.isAgedToLost;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.hamcrest.MatcherAssert.assertThat;

import api.support.http.IndividualResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.ItemBuilder;
import io.vertx.core.json.JsonObject;
import lombok.val;

class CloseAgedToLostLoanWhenLostItemFeesAreClosedApiTests extends APITests {
  private IndividualResource loan;
  private IndividualResource item;

  @BeforeEach
  public void createLoanAndAgeToLost() {
    feeFineOwnerFixture.cd1Owner();
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withSetCost(10.0)
        .chargeProcessingFeeWhenAgedToLost(15.00));

    item = result.getItem();
    loan = result.getLoan();
  }

  @Test
  void shouldCloseLoanWhenAllFeesClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
  }

  @Test
  void shouldIgnoreFeesThatAreNotDueToLosingItem() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    final IndividualResource manualFee = feeFineAccountFixture
      .createManualFeeForLoan(loan, 10.00);

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());

    assertThat(accountsClient.getById(manualFee.getId()).getJson(), isOpen());
  }

  @Test
  void shouldNotCloseLoanWhenProcessingFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isAgedToLost());
  }

  @Test
  void shouldNotCloseLoanIfSetCostFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isAgedToLost());
  }

  @Test
  void shouldNotCloseLoanIfActualCostFeeShouldBeCharged() {
    item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    ageToLostFixture.ageToLost();

    updateLostPolicyToUseActualCost();

    ageToLostFixture.chargeFees();

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isAgedToLost());
  }

  @Test
  void shouldNotCloseCheckedOutLoan() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  private void updateLostPolicyToUseActualCost() {
    val lostItemPolicyId = getUUIDProperty(loan.getJson(), "lostItemPolicyId");
    val lostItemPolicy = lostItemFeePolicyClient.getById(lostItemPolicyId).getJson();

    lostItemPolicy.put("chargeAmountItem", new JsonObject()
      .put("amount", 10.00)
      .put("chargeType", "actualCost"));

    lostItemFeePolicyClient.replace(lostItemPolicyId, lostItemPolicy);
  }
}
