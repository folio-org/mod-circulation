package api.handlers;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledActualCostExpiration;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.ZonedDateTime;

import org.folio.circulation.support.utils.ClockUtil;

import api.support.APITests;
import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.http.IndividualResource;
import api.support.http.TimedTaskClient;

public class CloseLostLoanWhenLostItemFeesAreClosed extends APITests {
  protected IndividualResource loan;
  protected IndividualResource item;
  private final TimedTaskClient timedTaskClient =  new TimedTaskClient(getOkapiHeadersFromContext());

  protected void payProcessingFeeAndCheckThatLoanIsClosedAsExpired() {
    payProcessingFeeAndRunScheduledActualCostExpiration(ClockUtil.getZonedDateTime().plusMonths(2));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
  }

  protected void payProcessingFeeAndCheckThatLoanIsOpenAsNotExpired() {
    payProcessingFeeAndRunScheduledActualCostExpiration(ClockUtil.getZonedDateTime().plusWeeks(1));

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
  }

  protected void runScheduledActualCostExpirationAndCheckThatLoanIsOpen() {
    mockClockManagerToReturnFixedDateTime(ClockUtil.getZonedDateTime().plusWeeks(1));
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    timedTaskClient.start(scheduledActualCostExpiration(), 204,
      "scheduled-actual-cost-expiration");
    mockClockManagerToReturnDefaultDateTime();

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
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

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
  }

  protected void payLostItemActualCostFeeAndProcessingFeeAndCheckThatLoanIsClosed() {
    createLostItemFeeActualCostAccount(10.0, loan);
    feeFineAccountFixture.payLostItemActualCostFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());

    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isClosed());
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
}
