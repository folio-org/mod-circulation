package org.folio.circulation.domain.policy.lostitem;

import static org.folio.circulation.domain.policy.Period.from;
import static org.folio.circulation.domain.policy.Period.hours;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.LostItemFeePolicyBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

@RunWith(JUnitParamsRunner.class)
public class LostItemPolicyTest {

  @Test
  @Parameters( {
    "Minutes, 78",
    "Hours, 9",
    "Days, 66",
    "Weeks, 23",
    "Months, 13",
  })
  public void shouldNotAgeItemToLostIfDueDateIsInTheFuture(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    assertFalse(lostItemPolicy.canAgeLoanToLost(now(UTC).plusMinutes(1)));
  }

  @Test
  @Parameters( {
    "Minutes, 43",
    "Hours, 12",
    "Days, 29",
    "Weeks, 1",
    "Months, 5",
  })
  public void shouldAgeItemToLostIfAgeToLostPeriodsHavePassedSinceDueDate(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  @Parameters( {
    "Minutes, 123",
    "Hours, 99",
    "Days, 64",
    "Weeks, 2",
    "Months, 3",
  })
  public void shouldAgeItemToLostIfAgeToLostPeriodsArePassingExactlyNowSinceDueDate(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(loanDueDate));
  }

  @Test
  public void shouldNotAgeItemToLostIfPeriodIsMissingInPolicy() {
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(null);

    assertFalse(lostItemPolicy.canAgeLoanToLost(now(UTC)));
  }

  @Test
  @Parameters( {
    "Minutes, 123",
    "Hours, 99",
    "Days, 64",
    "Weeks, 2",
    "Months, 3",
  })
  public void shouldRefundLostFeesIfPeriodHasNotPassed(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime lostDateTime = now(UTC).minus(from(duration / 2, interval).timePeriod());
    assertTrue(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @Test
  @Parameters( {
    "Minutes, 656",
    "Hours, 6",
    "Days, 98",
    "Weeks, 43",
    "Months, 44",
  })
  public void shouldRefundLostFeesIfPeriodIsPassing(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime lostDateTime = now(UTC).minus(period.timePeriod());
    assertTrue(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @Test
  @Parameters( {
    "Minutes, 656",
    "Hours, 6",
    "Days, 98",
    "Weeks, 43",
    "Months, 44",
  })
  public void shouldNotRefundLostFeesIfPeriodHasPassed(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime lostDateTime = now(UTC).minus(period.timePeriod()).minusSeconds(1);
    assertFalse(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @Test
  public void shouldNotAgeItemToLostIfActualCostIsUsed() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withActualCost(10.0);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertFalse(lostItemPolicy.canAgeLoanToLost(now(UTC)));
  }

  @Test
  public void shouldNotChargeFeesWhenChargedImmediatelyAndNoFees() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(null)
      .withNoChargeAmountItem()
      .doNotChargeProcessingFee();

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertFalse(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  @Test
  public void shouldChargeFeesWhenDelayedBillingAndNoFees() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(hours(2))
      .withNoChargeAmountItem()
      .doNotChargeProcessingFee();

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertTrue(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  @Test
  public void shouldChargeFeesWhenDelayedBillingAndThereIsItemFee() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(hours(2))
      .withSetCost(10d)
      .doNotChargeProcessingFee();

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertTrue(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  @Test
  public void shouldChargeFeesWhenDelayedBillingAndThereIsProcessingFee() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withPatronBilledAfterAgedLost(hours(2))
      .withNoChargeAmountItem()
      .withLostItemProcessingFee(5d);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertTrue(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  @Test
  public void shouldChargeFeesWhenImmediateBillingAndThereIsItemFee() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .billPatronImmediatelyWhenAgedToLost()
      .withSetCost(10d)
      .doNotChargeProcessingFee();

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertTrue(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  @Test
  public void shouldChargeFeesWhenImmediateBillingAndThereIsProcessingFee() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .billPatronImmediatelyWhenAgedToLost()
      .withNoChargeAmountItem()
      .withLostItemProcessingFee(5d);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertTrue(lostItemPolicy.shouldChargeFeesWhenAgedToLost());
  }

  private LostItemPolicy lostItemPolicyWithAgePeriod(Period period) {
    return LostItemPolicy.from(new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(period)
      .create());
  }
}
