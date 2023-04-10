package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isClosedCancelled;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFeeActualCost;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import api.support.builders.AccountBuilder;
import api.support.builders.FeefineActionsBuilder;
import api.support.fixtures.AgeToLostFixture;
import api.support.fixtures.OverrideRenewalFixture;
import api.support.http.IndividualResource;
import io.vertx.core.json.JsonObject;
import lombok.val;

class OverrideRenewAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  @Autowired
  private OverrideRenewalFixture overrideRenewalFixture;

  public OverrideRenewAgedToLostItemTest() {
    super("Cancelled item renewed", "Lost item was renewed");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
     ZonedDateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    overrideRenewalFixture.overrideRenewalByBarcode(result.getLoan(),
      servicePointsFixture.cd1().getId());
  }

  @Test
  void canOverrideRenewalAfterAgeToLostAndRefundsWithLostItemActualCostFee() {
    final double itemFeeActualCost = 10.0;
    final double itemProcessingFee = 5.0;
    val policy = lostItemFeePoliciesFixture.facultyStandardPolicy()
      .withName("shouldChargeOverdueFine")
      .withActualCost(itemFeeActualCost)
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterItemAgedToLost(minutes(5))
      .chargeProcessingFeeWhenAgedToLost(itemProcessingFee)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    createLostItemFeeActualCostAccount(result.getLoan(), itemFeeActualCost);

    assertThat(feeFineActionsClient.getAll(), hasSize(2));
    assertThat(result.getLoan(), hasLostItemFeeActualCost(isOpen(itemFeeActualCost)));
    assertThat(result.getLoan(), hasLostItemProcessingFee(isOpen(itemProcessingFee)));

    feeFineAccountFixture.payLostItemActualCostFee(result.getLoanId(), 3.0);
    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId(), 3.0);
    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime().plusMonths(8));

    IndividualResource loan = loansClient.get(result.getLoanId());
    assertThat(loan.getJson().getString("action"), is("renewedThroughOverride"));
    assertThat(loan, hasLostItemFeeActualCost(isClosedCancelled(cancellationReason, itemFeeActualCost)));
    assertThat(loan, hasLostItemProcessingFee(isClosedCancelled(cancellationReason, itemProcessingFee)));
  }

  @Test
  void canOverrideRenewalAfterAgeToLostAndRefundsWithOnlyLostItemActualCostFee() {
    final double itemFeeActualCost = 10.0;
    val policy = lostItemFeePoliciesFixture.facultyStandardPolicy()
      .withName("shouldChargeOverdueFine")
      .withActualCost(itemFeeActualCost)
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterItemAgedToLost(minutes(5))
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval()
      .withLostItemProcessingFee(1.0);

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);
    createLostItemFeeActualCostAccount(result.getLoan(), itemFeeActualCost);

    assertThat(feeFineActionsClient.getAll(), hasSize(2));
    assertThat(result.getLoan(), hasLostItemFeeActualCost(isOpen(itemFeeActualCost)));

    feeFineAccountFixture.payLostItemActualCostFee(result.getLoanId(), 3.0);
    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime().plusMonths(8));

    IndividualResource loan = loansClient.get(result.getLoanId());
    assertThat(loan.getJson().getString("action"), is("renewedThroughOverride"));
    assertThat(loan, hasLostItemFeeActualCost(isClosedCancelled(cancellationReason,
      itemFeeActualCost)));
  }

  private void createLostItemFeeActualCostAccount(IndividualResource loan, double amount) {
    var account = feeFineAccountFixture.createLostItemFeeActualCostAccount(amount, loan,
      feeFineTypeFixture.lostItemActualCostFee(), feeFineOwnerFixture.cd1Owner(),
      "stuffInfo", "patronInfo");

    JsonObject actualCostRecord = actualCostRecordsClient.getAll().get(0);
    String recordId = actualCostRecord.getString("id");
    actualCostRecord.put("accountId", account.getId());
    actualCostRecordsClient.replace(UUID.fromString(recordId), actualCostRecord);
  }
}
