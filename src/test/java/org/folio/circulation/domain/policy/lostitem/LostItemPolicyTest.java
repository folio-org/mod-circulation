package org.folio.circulation.domain.policy.lostitem;

import static java.time.Clock.fixed;
import static org.folio.circulation.domain.policy.Period.from;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.folio.circulation.support.ClockManager.getClockManager;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;

import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.LostItemFeePolicyBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;

@RunWith(JUnitParamsRunner.class)
public class LostItemPolicyTest {

  @Before
  public void useDefaultClocks() {
    getClockManager().setDefaultClock();
  }

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

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC).plusMinutes(1)));
  }

  @Test
  @Parameters( {
    "Minutes, 43",
    "Hours, 12",
    "Days, 29",
    "Weeks, 1",
    "Months, 5",
  })
  public void shouldAgeToLostIfAgeToLostPeriodsHavePassedSinceDueDateAndItemNotRecalled(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

    assertTrue(lostItemPolicy.canAgeLoanToLost(false, loanDueDate));
  }

  @Test
  @Parameters( {
    "Minutes, 43",
    "Hours, 12",
    "Days, 29",
    "Weeks, 1",
    "Months, 5",
  })
  public void shouldAgeToLostIfAgeToLostPeriodsHavePassedSinceDueDateAndItemRecalled(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithRecallAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

    assertTrue(lostItemPolicy.canAgeLoanToLost(true, loanDueDate));
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

    assertTrue(lostItemPolicy.canAgeLoanToLost(false, loanDueDate));
  }

  @Test
  public void shouldNotAgeItemToLostIfPeriodIsMissingInPolicy() {
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(null);

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC)));
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
    "Months, 5",
  })
  public void shouldRefundLostFeesIfPeriodIsPassing(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime now = now(UTC);
    getClockManager().setClock(fixed(Instant.ofEpochMilli(now.getMillis()), ZoneOffset.UTC));

    final DateTime lostDateTime = now.minus(period.timePeriod());
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

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC)));
  }

  @Test
  @Parameters( {
    "Minutes, 0",
    "Hours, 0",
    "Days, 0",
    "Weeks, 0",
    "Months, 0",
    "null, null"
  })
  public void canCalculateBillingDateWhenPatronIsBilledImmediatelyForNotRecalledItem(
    @Nullable String interval, @Nullable Integer duration) {

    final Period billPatronInterval = duration == null && interval == null
      ? null : Period.from(duration, interval);

    final DateTime agedToLostDate = DateTime.now();

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterItemAgedToLost(billPatronInterval)
        .withSetCost(10.0)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .create());

    final DateTime actualBillingDate = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(false, agedToLostDate);

    assertThat(actualBillingDate, is(agedToLostDate));
  }

  @Test
  public void canCalculateBillingDateWhenPatronBillingIsDelayedForNotRecalledItem() {
    final Period billPatronAfterPeriod = Period.weeks(1);
    final DateTime ageToLostDate = DateTime.now();
    final DateTime expectedBillingDate = ageToLostDate.plus(billPatronAfterPeriod.timePeriod());

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterItemAgedToLost(billPatronAfterPeriod)
        .withSetCost(10.0)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .create());

    final DateTime actualBillingDate = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(false, ageToLostDate);

    assertThat(actualBillingDate, is(expectedBillingDate));
  }

  @Test
  public void canUseRecallIntervalForBillingDate() {
    final Period billPatronAfterPeriod = Period.weeks(2);
    final DateTime ageToLostDate = DateTime.now();
    final DateTime expectedBillingDate = ageToLostDate.plus(billPatronAfterPeriod.timePeriod());

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterRecalledItemAgedLost(billPatronAfterPeriod)
        .withSetCost(10.0)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .create());

    final DateTime actualBillingDate = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(true, ageToLostDate);

    assertThat(actualBillingDate, is(expectedBillingDate));
  }

  @Test
  public void shouldNotUseRecallIntervalForNotRecalledItem() {
    final Period ageToLostBillingPeriod = Period.weeks(1);
    final Period recallBillingPeriod = Period.weeks(2);
    final DateTime ageToLostDate = DateTime.now();
    final DateTime expectedBillingDate = ageToLostDate.plus(ageToLostBillingPeriod.timePeriod());

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterRecalledItemAgedLost(recallBillingPeriod)
        .withPatronBilledAfterItemAgedToLost(ageToLostBillingPeriod)
        .withSetCost(10.0)
        .doNotChargeProcessingFeeWhenAgedToLost()
        .create());

    final DateTime actualBillingDate = lostItemPolicy
      .calculateDateTimeWhenPatronBilledForAgedToLost(false, ageToLostDate);

    assertThat(actualBillingDate, is(expectedBillingDate));
  }

  @Test
  public void ageToLostProcessingFeeIsNotChargeableIfAmountIsSetButFlagIsFalse() {
    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .doNotChargeProcessingFeeWhenAgedToLost()
        .chargeProcessingFeeWhenDeclaredLost(10.0)
        .create());

    assertFalse(lostItemPolicy.getAgeToLostProcessingFee().isChargeable());
    assertTrue(lostItemPolicy.getDeclareLostProcessingFee().isChargeable());
  }

  @Test
  public void ageToLostProcessingFeeIsChargeableEvenIfDeclaredLostFlagIsFalse() {
    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .doNotChargeProcessingFeeWhenDeclaredLost()
        .chargeProcessingFeeWhenAgedToLost(10.00)
        .create());

    assertTrue(lostItemPolicy.getAgeToLostProcessingFee().isChargeable());
    assertFalse(lostItemPolicy.getDeclareLostProcessingFee().isChargeable());
  }

  private LostItemPolicy lostItemPolicyWithAgePeriod(Period period) {
    return LostItemPolicy.from(new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(period)
      .create());
  }

  private LostItemPolicy lostItemPolicyWithRecallAgePeriod(Period period) {
    return LostItemPolicy.from(new LostItemFeePolicyBuilder()
      .withRecalledItemAgedToLostAfterOverdue(period)
      .create());
  }
}
