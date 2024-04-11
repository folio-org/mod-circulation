package api.handlers;

import static api.support.APITestContext.getOkapiHeadersFromContext;
import static api.support.http.InterfaceUrls.scheduledActualCostExpiration;
import static api.support.matchers.ItemMatchers.hasStatus;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanMatchers.isClosed;
import static api.support.matchers.LoanMatchers.isOpen;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.support.utils.ClockUtil;

import api.support.APITests;
import api.support.http.IndividualResource;
import api.support.http.TimedTaskClient;
import io.vertx.core.json.JsonObject;
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
    feeFineAccountFixture.createLostItemFeeActualCostAccount(10.0, loan,
      feeFineTypeFixture.lostItemActualCostFee(), feeFineOwnerFixture.cd1Owner(),
      "staffInfo", "patronInfo");
    feeFineAccountFixture.payLostItemActualCostFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsClosedAsLostAndPaid();
  }

  protected void payLostItemActualCostFeeAndProcessingFeeAndCheckThatLoanIsClosed() {
    feeFineAccountFixture.createLostItemFeeActualCostAccount(10.0, loan,
      feeFineTypeFixture.lostItemActualCostFee(), feeFineOwnerFixture.cd1Owner(),
      "staffInfo", "patronInfo");
    feeFineAccountFixture.payLostItemActualCostFee(loan.getId());
    feeFineAccountFixture.payLostItemProcessingFee(loan.getId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(loan.getId());
    assertThatLoanIsClosedAsLostAndPaid();
  }

  protected JsonObject assertThatLoanIsClosedAsLostAndPaid() {
    JsonObject loan = loansFixture.getLoanById(this.loan.getId()).getJson();
    assertThat(loan, isClosed());
    assertNotNull(loan.getString("returnDate"));
    assertThat(itemsClient.getById(item.getId()).getJson(), isLostAndPaid());

    return loan;
  }

  protected void assertThatLoanIsOpenAndLost() {
    assertThat(loansFixture.getLoanById(loan.getId()).getJson(), isOpen());
    assertThat(itemsClient.getById(item.getId()).getJson(), hasStatus(lostItemStatus.getValue()));
  }

  protected void cancelActualCostRecord() {
    JsonObject record = actualCostRecordsClient.getAll().get(0);
    record = record.put("status", ActualCostRecord.Status.CANCELLED.getValue());
    actualCostRecordsClient.replace(UUID.fromString(record.getString("id")), record);

    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEventForActualCostFee(loan.getId());
  }
}
