package api.loans.scenarios;

import static api.support.matchers.AccountMatchers.isRefundedFully;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

import org.joda.time.DateTime;
import org.junit.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.fixtures.AgeToLostFixture;
import io.vertx.core.json.JsonObject;

public class CheckInAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  public CheckInAgedToLostItemTest() {
    super("Cancelled item returned");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
    DateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    final JsonObject loan = checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(result.getItem())
      .at(servicePointsFixture.cd1())
      .on(actionDate))
      .getLoan();

    assertThat(loan, nullValue());
  }

  @Test
  public void shouldRefundFeesForLostAndPaidItemsThatWasAgedToLost() {
    final double processingFee = 12.99;
    final double itemFee = 12.99;

    final var policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("canRefundFeesForLostAndPaidItemsThatWasAgedToLost")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withSetCost(itemFee)
      .withNoFeeRefundInterval();

    final var result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemFee(result.getLoanId());
    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());
    eventSubscribersFixture.publishLoanRelatedFeeFineClosedEvent(result.getLoanId());

    assertThat(itemsClient.get(result.getItem()).getJson(), isLostAndPaid());

    final var response = checkInFixture.checkInByBarcode(result.getItem());

    assertThat(response.getLoan(), nullValue());
    assertThat(result.getLoan(), hasLostItemFee(isRefundedFully(itemFee)));
    assertThat(result.getLoan(), hasLostItemProcessingFee(isRefundedFully(processingFee)));
  }
}
