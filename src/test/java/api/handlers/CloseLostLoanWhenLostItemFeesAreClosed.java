package api.handlers;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledActualCostExpiration;
import static api.support.matchers.ItemMatchers.hasStatus;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.IndividualResource;
import api.support.http.TimedTaskClient;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CloseLostLoanWhenLostItemFeesAreClosed extends APITests {
  private final ItemStatus lostItemStatus;
  protected IndividualResource loan;
  protected IndividualResource item;
  private final TimedTaskClient timedTaskClient =  new TimedTaskClient(getOkapiHeadersFromContext());

  protected void payProcessingFeeAndCheckThatLoanIsClosedAsExpired() {
    payProcessingFeeAndRunScheduledActualCostExpiration(ClockUtil.getZonedDateTime().plusMonths(2));
    assertThatLoanIsClosedAsLostAndPaid();
  }

  protected void payProcessingFeeAndCheckThatLoanIsOpenAsNotExpired() {
    payProcessingFeeAndRunScheduledActualCostExpiration(ClockUtil.getZonedDateTime().plusWeeks(1));
    assertThatLoanIsOpenAndLost();
  }

  protected void runScheduledActualCostExpirationAndCheckThatLoanIsOpen() {
    mockClockManagerToReturnFixedDateTime(ClockUtil.getZonedDateTime().plusWeeks(1));
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    timedTaskClient.start(scheduledActualCostExpiration(), 204,
      "scheduled-actual-cost-expiration");
    mockClockManagerToReturnDefaultDateTime();

    assertThatLoanIsOpenAndLost();
  }

  private void payProcessingFeeAndRunScheduledActualCostExpiration(ZonedDateTime dateTime) {
    mockClockManagerToReturnFixedDateTime(dateTime);
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    timedTaskClient.start(scheduledActualCostExpiration(), 204,
      "scheduled-actual-cost-expiration");
    mockClockManagerToReturnDefaultDateTime();
  }

  protected void payLostItemActualCostFeeAndCheckThatLoanIsClosed() {
    createLostItemFeeActualCostAccount(10.0, loan);
    feeFineAccountFixture.payLostItemActualCostFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsClosedAsLostAndPaid();
  }

  protected void payLostItemActualCostFeeAndProcessingFeeAndCheckThatLoanIsClosed() {
    createLostItemFeeActualCostAccount(10.0, loan);
    feeFineAccountFixture.payLostItemActualCostFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsClosedAsLostAndPaid();
  }

  protected void createLostItemFeeActualCostAccount(double amount, IndividualResource loan) {
    IndividualResource account = accountsClient.create(new AccountBuilder()
      .withLoan(loan)
      .withAmount(amount)
      .withRemainingFeeFine(amount)
      .feeFineStatusOpen()
      .withFeeFineActualCostType()
      .withFeeFine(feeFineTypeFixture.lostItemActualCostFee())
      .withOwner(feeFineOwnerFixture.cd1Owner())
      .withPaymentStatus("Outstanding"));

    feeFineActionsClient.create(new FeefineActionsBuilder()
      .forAccount(account.getId())
      .withBalance(amount)
      .withActionAmount(amount)
      .withActionType("Lost item fee (actual cost)"));
  }

  protected void assertThatLoanIsClosedAsLostAndPaid() {
    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());
  }

  protected void assertThatLoanIsOpenAndLost() {
    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), hasStatus(lostItemStatus.getValue()));
  }
}
