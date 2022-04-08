package api.loans.scenarios;

import static api.support.PubsubPublisherTestUtils.assertThatPublishedLoanLogRecordEventsAreValid;
import static api.support.matchers.AccountMatchers.isCancelledItemReturned;
import static api.support.matchers.AccountMatchers.isOpen;
import static api.support.matchers.ItemMatchers.isLostAndPaid;
import static api.support.matchers.LoanAccountMatcher.hasLostItemFee;
import static api.support.matchers.LoanAccountMatcher.hasLostItemProcessingFee;
import static api.support.matchers.LoanAccountMatcher.hasOverdueFine;
import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasNoJsonPath;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import api.support.http.IndividualResource;
import lombok.val;
import org.folio.circulation.support.utils.ClockUtil;
import org.hamcrest.core.Is;
import org.junit.jupiter.api.Test;

import api.support.builders.CheckInByBarcodeRequestBuilder;
import api.support.fixtures.AgeToLostFixture;
import io.vertx.core.json.JsonObject;

class CheckInAgedToLostItemTest extends RefundAgedToLostFeesTestBase {
  public CheckInAgedToLostItemTest() {
    super("Cancelled item returned");
  }

  @Override
  protected void performActionThatRequiresRefund(AgeToLostFixture.AgeToLostResult result,
    ZonedDateTime actionDate) {

    mockClockManagerToReturnFixedDateTime(actionDate);

    final JsonObject loan = checkInFixture.checkInByBarcode(new CheckInByBarcodeRequestBuilder()
      .forItem(result.getItem())
      .at(servicePointsFixture.cd1())
      .on(actionDate))
      .getLoan();

    assertThat(loan, notNullValue());
  }

  @Test
  void shouldRefundFeesForLostAndPaidItemsThatWasAgedToLost() {
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
    List<JsonObject> typeAction = feeFineActionsClient.getAll().stream()
      .filter(e -> "Refunded fully".equals(e.getString("typeAction")))
      .collect(Collectors.toList());

    assertThat(response.getLoan(), nullValue());
    assertThat(typeAction.size(), is(2));
    typeAction.forEach(action -> {
      assertThat(action.getDouble("amountAction"), Is.is(processingFee));
      assertThat(action.getDouble("balance"), Is.is(itemFee));
    });
  }

  @Test
  void subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemAlreadyReturned() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("subsequentRunOfChargeFeeProcessNotAssignsFeesWhenItemWasRemoved")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime());

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemReturned(processingFee)));

    // Run the charging process again
    ageToLostFixture.chargeFees();

    // make sure fees are not assigned for the loan again
    assertThat(loan, not(hasLostItemProcessingFee(isOpen(processingFee))));

    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldRefundPartiallyPaidAmountAndCancelRemaining() {
    final double setCostFee = 10.55;
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldRefundPartiallyPaidAmountAndCancelRemaining")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .withSetCost(setCostFee)
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.transferLostItemFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime());

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemFee(isCancelledItemReturned(setCostFee)));
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemReturned(processingFee)));

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  @Test
  void shouldChargeOverdueFine() {
    final double processingFee = 12.99;

    val policy = lostItemFeePoliciesFixture.ageToLostAfterOneMinutePolicy()
      .withName("shouldChargeOverdueFine")
      .chargeProcessingFeeWhenAgedToLost(processingFee)
      .chargeOverdueFineWhenReturned()
      .withNoFeeRefundInterval();

    val result = ageToLostFixture.createLoanAgeToLostAndChargeFees(policy);

    feeFineAccountFixture.payLostItemProcessingFee(result.getLoanId());

    performActionThatRequiresRefund(result, ClockUtil.getZonedDateTime().plusMonths(8));

    final IndividualResource loan = result.getLoan();
    assertThat(loan, hasLostItemProcessingFee(isCancelledItemReturned(processingFee)));
    assertThat(loan, hasOverdueFine());

    assertThatBillingInformationRemoved(loan);
    assertThatPublishedLoanLogRecordEventsAreValid(loansClient.getById(loan.getId()).getJson());
  }

  private void assertThatBillingInformationRemoved(IndividualResource loan) {
    assertThat(loansStorageClient.get(loan).getJson().toString(), allOf(
      hasNoJsonPath("agedToLostDelayedBilling.lostItemHasBeenBilled"),
      hasNoJsonPath("agedToLostDelayedBilling.dateLostItemShouldBeBilled")
    ));
  }
}
