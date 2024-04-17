package api.handlers;

import static api.support.fakes.FakePubSub.getPublishedEventsAsList;
import static api.support.fakes.PublishedEvents.byEventType;
import static api.support.matchers.EventMatchers.isValidLoanClosedEvent;
import static api.support.matchers.ItemMatchers.isAvailable;
import static api.support.matchers.ItemMatchers.isCheckedOut;
import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.folio.circulation.domain.EventType.LOAN_CLOSED;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.DeclareItemLostRequestBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;

class CloseDeclaredLostLoanWhenLostItemFeesAreClosedApiTests extends CloseLostLoanWhenLostItemFeesAreClosed {

  public CloseDeclaredLostLoanWhenLostItemFeesAreClosedApiTests() {
    super(ItemStatus.DECLARED_LOST);
  }

  @BeforeEach
  public void createLoanAndDeclareItemLost() {
    mockClockManagerToReturnDefaultDateTime();
    UUID servicePointId = servicePointsFixture.cd1().getId();
    useLostItemPolicy(lostItemFeePoliciesFixture.chargeFee().getId());

    item = itemsFixture.basedUponSmallAngryPlanet();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));
  }

  @Test
  void shouldCloseLoanWhenAllFeesClosed() {
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
  void shouldDisregardNonLostFeeTypes() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    final IndividualResource manualFee = feeFineAccountFixture
      .createManualFeeForLoan(loan, 10.00);

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsClosedAsLostAndPaid();

    assertThat(accountsClient.getById(manualFee.getId()).getJson(), isOpen());
  }

  @Test
  void shouldNotCloseLoanWhenProcessingFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsOpenAndLost();
  }

  @Test
  void shouldNotCloseLoanIfSetCostFeeIsNotClosed() {
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsOpenAndLost();
  }

  @Test
  void shouldCloseLoanIfActualCostFeeHasBeenPaidWithoutProcessingFee() {
    UUID servicePointId = servicePointsFixture.cd2().getId();
    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder().withName("test")
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .withActualCost(10.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));

    payLostItemActualCostFeeAndCheckThatLoanIsClosed();
  }

  @Test
  void shouldCloseLoanIfActualCostFeeAndProcessingFeeHaveBeenPaid() {
    UUID servicePointId = servicePointsFixture.cd2().getId();
    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder().withName("test")
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(10.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));

    payLostItemActualCostFeeAndProcessingFeeAndCheckThatLoanIsClosed();
  }

  @Test
  void shouldCloseLoanIfChargingPeriodExpiredAndProcessingFeeHasBeenPaid() {
    UUID servicePointId = servicePointsFixture.cd2().getId();

    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder().withName("test")
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(10.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));

    payProcessingFeeAndCheckThatLoanIsClosedAsExpired();
  }

  @Test
  void shouldNotCloseDeclaredLostLoanIfChargingPeriodExpiredAndProcessingFeeHasNotBeenPaid() {
    UUID servicePointId = servicePointsFixture.cd2().getId();

    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder().withName("test")
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(10.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));

    runScheduledActualCostExpirationAndCheckThatLoanIsOpen();
  }

  @Test
  void shouldNotCloseLoanIfChargingPeriodHasNotExpiredAndProcessingFeeHasBeenPaid() {
    UUID servicePointId = servicePointsFixture.cd2().getId();

    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder().withName("test")
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(10.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));

    payProcessingFeeAndCheckThatLoanIsOpenAsNotExpired();
  }

  @Test
  void shouldNotCloseRefundedLoan() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());

    checkInFixture.checkInByBarcode(item);

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), allOf(
      isClosed(),
      hasJsonPath("action", "checkedin")));
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), allOf(
      isClosed(),
      hasJsonPath("action", "checkedin")));
    assertThat(itemsClient.getById(item.getId()).getJson(), isAvailable());
  }

  @Test
  void shouldNotCloseCheckedOutLoan() {
    item = itemsFixture.basedUponNod();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), isCheckedOut());
  }

  @Test
  void shouldIgnoreErrorWhenNoLoanIdSpecifiedInPayload() {
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(null, UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  void shouldIgnoreErrorWhenNonExistentLoanIdProvided() {
    final UUID loanId = UUID.randomUUID();
    final Response response = eventSubscribersFixture
      .attemptPublishLoanRelatedFeeFineClosedEvent(loanId,
        UUID.randomUUID());

    assertThat(response.getStatusCode(), is(204));
  }

  @Test
  void shouldNotPublishLoanClosedEventWhenLoanIsOriginallyClosed() {
    feeFineAccountFixture.payLostItemFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    checkInFixture.checkInByBarcode(item);
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThat(getPublishedEventsAsList(byEventType(LOAN_CLOSED)), empty());
  }

  @Test
  void shouldCloseLoanWhenActualCostRecordIsCancelled() {
    UUID servicePointId = servicePointsFixture.cd2().getId();
    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withName("test")
        .withActualCost(0.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponDunkirk();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));
    cancelActualCostRecord();

    assertThatLoanIsClosedAsLostAndPaid();
  }

  @Test
  void shouldNotCloseLoanWhenActualCostRecordIsCancelledButProcessingFeeWasNotPaid() {
    UUID servicePointId = servicePointsFixture.cd2().getId();
    UUID actualCostLostItemFeePolicyId = lostItemFeePoliciesFixture.create(
      new LostItemFeePolicyBuilder()
        .withName("test")
        .chargeProcessingFeeWhenDeclaredLost(10.00)
        .withActualCost(0.0)
        .withLostItemChargeFeeFine(Period.weeks(2))).getId();
    useLostItemPolicy(actualCostLostItemFeePolicyId);

    item = itemsFixture.basedUponDunkirk();
    loan = checkOutFixture.checkOutByBarcode(item, usersFixture.jessica());
    declareLostFixtures.declareItemLost(new DeclareItemLostRequestBuilder()
      .withServicePointId(servicePointId)
      .forLoanId(loan.getId()));
    cancelActualCostRecord();

    assertThatLoanIsOpenAndLost();
  }
}
