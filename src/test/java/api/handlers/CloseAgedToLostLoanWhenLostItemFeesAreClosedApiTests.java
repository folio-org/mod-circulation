package api.handlers;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.http.CqlQuery.exactMatch;
import static api.support.matchers.EventMatchers.isValidLoanClosedEvent;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.folio.circulation.domain.EventType.LOAN_CLOSED;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getUUIDProperty;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.services.agedtolost.LoanToChargeFees;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.ItemBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

class CloseAgedToLostLoanWhenLostItemFeesAreClosedApiTests extends CloseLostLoanWhenLostItemFeesAreClosed {

  public CloseAgedToLostLoanWhenLostItemFeesAreClosedApiTests() {
    super(ItemStatus.AGED_TO_LOST);
  }

  @BeforeEach
  public void createLoanAndAgeToLost() {
    feeFineOwnerFixture.cd1Owner();
  }

  @Test
  void shouldCloseLoanWhenAllFeesClosed() {
    createAgeToLostLoanWithSetCostPolicy();
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    var returnDate = ClockUtil.getZonedDateTime();
    mockClockManagerToReturnFixedDateTime(returnDate);
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    JsonObject loan = assertThatLoanIsClosedAsLostAndPaid();
    assertThat(loan.getString("returnDate"), is(returnDate.toString()));
    mockClockManagerToReturnDefaultDateTime();

    List<JsonObject> loanClosedEvents = getPublishedEventsAsList(byEventType(LOAN_CLOSED));
    assertThat(loanClosedEvents, hasSize(1));
    assertThat(loanClosedEvents.get(0), isValidLoanClosedEvent(loan));
  }

  @Test
  void shouldIgnoreFeesThatAreNotDueToLosingItem() {
    createAgeToLostLoanWithSetCostPolicy();
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
    createAgeToLostLoanWithSetCostPolicy();
    feeFineAccountFixture.payLostItemFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsOpenAndLost();
  }

  @Test
  void shouldNotCloseLoanIfSetCostFeeIsNotClosed() {
    createAgeToLostLoanWithSetCostPolicy();
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsOpenAndLost();
  }

  @Test
  void shouldNotCloseLoanIfActualCostFeeShouldBeCharged() {
    createAgeToLostLoanWithSetCostPolicy();
    item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());

    ageToLostFixture.ageToLost();

    updateLostPolicyToUseActualCost();

    ageToLostFixture.chargeFees();

    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsOpenAndLost();
  }

  @Test
  void shouldNotFailWhenAgedToLostLoanHasNonexistentItem() {
    createAgeToLostLoanWithSetCostPolicy();
    var item = itemsFixture.basedUponNod(ItemBuilder::withRandomBarcode);
    var loan = checkOutFixture.checkOutByBarcode(item, usersFixture.steve());
    ageToLostFixture.ageToLost();
    itemsClient.delete(item.getId());

    ageToLostFixture.chargeFees();

    JsonObject loanById = loansFixture.getLoanById(loan.getId()).getJson();
    assertThat(loanById, isOpen());
    assertThat(loanById.getString("itemId"), is(item.getId().toString()));
    assertThat(accountsClient.getMany(exactMatch("loanId", loan.getId().toString())).size(),
      is(0));
  }

  @Test
  void getOwnerServicePointIdShouldNotFailIfItemDoesNotExist() {
    createAgeToLostLoanWithSetCostPolicy();
    JsonObject loanJson = new JsonObject().put("id", UUID.randomUUID().toString());
    assertThat(LoanToChargeFees.usingLoan(Loan.from(loanJson)).getPrimaryServicePointId(),
      nullValue());
  }

  @Test
  void shouldNotCloseCheckedOutLoan() {
    createAgeToLostLoanWithSetCostPolicy();
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  void shouldCloseAgedToLostLoanIfActualCostFeeHasBeenPaidWithoutProcessingFee() {
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0));

    item = result.getItem();
    loan = result.getLoan();

    payLostItemActualCostFeeAndCheckThatLoanIsClosed();
  }

  @Test
  void shouldCloseAgedToLostLoanIfActualCostFeeAndProcessingFeeHaveBeenPaid() {
    feeFineTypeFixture.lostItemProcessingFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0)
        .chargeProcessingFeeWhenAgedToLost((15.00)));
    item = result.getItem();
    loan = result.getLoan();

    payLostItemActualCostFeeAndProcessingFeeAndCheckThatLoanIsClosed();
  }

  @Test
  void shouldCloseLoanIfChargingPeriodExpiredAndProcessingFeeHasBeenPaid() {
    feeFineTypeFixture.lostItemProcessingFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0)
        .chargeProcessingFeeWhenAgedToLost(15.00)
        .withLostItemChargeFeeFine(Period.days(1)));
    item = result.getItem();
    loan = result.getLoan();

    payProcessingFeeAndCheckThatLoanIsClosedAsExpired();
  }

  @Test
  void shouldNotCloseLoanIfChargingPeriodHasNotExpiredAndProcessingFeeHasBeenPaid() {
    feeFineTypeFixture.lostItemProcessingFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0)
        .chargeProcessingFeeWhenAgedToLost(15.00)
        .withLostItemChargeFeeFine(Period.days(1)));
    item = result.getItem();
    loan = result.getLoan();

    payProcessingFeeAndCheckThatLoanIsOpenAsNotExpired();
  }

  @Test
  void shouldNotCloseAgedToLostLoanIfChargingPeriodExpiredAndProcessingFeeHasNotBeenPaid() {
    feeFineTypeFixture.lostItemProcessingFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0)
        .chargeProcessingFeeWhenAgedToLost(15.00));
    item = result.getItem();
    loan = result.getLoan();

    runScheduledActualCostExpirationAndCheckThatLoanIsOpen();
  }

  @Test
  void shouldCloseLoanWhenActualCostRecordIsCancelled() {
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(10.0));

    item = result.getItem();
    loan = result.getLoan();

    var returnDate = ClockUtil.getZonedDateTime();
    mockClockManagerToReturnFixedDateTime(returnDate);
    cancelActualCostRecord();
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    JsonObject loan = assertThatLoanIsClosedAsLostAndPaid();
    assertThat(loan.getString("returnDate"), is(returnDate.toString()));
    mockClockManagerToReturnDefaultDateTime();
  }

  @Test
  void shouldNotCloseLoanWhenActualCostRecordIsCancelledButProcessingFeeWasNotPaid() {
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemActualCostFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withActualCost(0.0)
        .chargeProcessingFeeWhenAgedToLost(15.00));

    item = result.getItem();
    loan = result.getLoan();
    cancelActualCostRecord();

    assertThatLoanIsOpenAndLost();
  }

  private void updateLostPolicyToUseActualCost() {
    val lostItemPolicyId = getUUIDProperty(loan.getJson(), "lostItemPolicyId");
    val lostItemPolicy = lostItemFeePolicyClient.getById(lostItemPolicyId).getJson();

    lostItemPolicy.put("chargeAmountItem", new JsonObject()
      .put("amount", 10.00)
      .put("chargeType", "actualCost"));

    lostItemFeePolicyClient.replace(lostItemPolicyId, lostItemPolicy);
  }

  private void createAgeToLostLoanWithSetCostPolicy() {
    feeFineTypeFixture.lostItemFee();
    feeFineTypeFixture.lostItemProcessingFee();
    feeFineTypeFixture.lostItemActualCostFee();
    var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(
      lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
        .withName("Age to lost policy")
        .withSetCost(10.0)
        .chargeProcessingFeeWhenAgedToLost(15.00));

    item = result.getItem();
    loan = result.getLoan();
  }
}
