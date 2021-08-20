package org.folio.circulation.domain.policy.lostitem;

import static java.time.Clock.fixed;
import static org.folio.circulation.domain.policy.Period.from;
import static org.folio.circulation.domain.policy.Period.minutes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneOffset;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import api.support.builders.LostItemFeePolicyBuilder;

class LostItemPolicyTest {

  @BeforeEach
  public void useDefaultClocks() {
    ClockUtil.setDefaultClock();
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 78",
    "Hours, 9",
    "Days, 66",
    "Weeks, 23",
    "Months, 13",
  })
  void shouldNotAgeItemToLostIfDueDateIsInTheFuture(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC).plusMinutes(1)));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 43",
    "Hours, 12",
    "Days, 29",
    "Weeks, 1",
    "Months, 5",
  })
  void shouldAgeToLostIfAgeToLostPeriodHasPassedSinceDueDateAndItemNotRecalled(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

    assertTrue(lostItemPolicy.canAgeLoanToLost(false, loanDueDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 43",
    "Hours, 12",
    "Days, 29",
    "Weeks, 1",
    "Months, 5",
  })
  void shouldAgeToLostIfAgeToLostPeriodHasPassedSinceDueDateAndItemRecalled(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithRecallAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod()).minusSeconds(1);

    assertTrue(lostItemPolicy.canAgeLoanToLost(true, loanDueDate));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 123",
    "Hours, 99",
    "Days, 64",
    "Weeks, 2",
    "Months, 3",
  })
  void shouldAgeItemToLostIfAgeToLostPeriodArePassingExactlyNowSinceDueDate(
    String interval, int duration) {

    final Period period = from(duration, interval);
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(period);

    final DateTime loanDueDate = now(UTC).minus(period.timePeriod());

    assertTrue(lostItemPolicy.canAgeLoanToLost(false, loanDueDate));
  }

  @Test
  void shouldNotAgeItemToLostIfPeriodIsMissingInPolicy() {
    final LostItemPolicy lostItemPolicy = lostItemPolicyWithAgePeriod(null);

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC)));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 123",
    "Hours, 99",
    "Days, 64",
    "Weeks, 2",
    "Months, 3",
  })
  void shouldRefundLostFeesIfPeriodHasNotPassed(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime lostDateTime = now(UTC).minus(from(duration / 2, interval).timePeriod());
    assertTrue(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 656",
    "Hours, 6",
    "Days, 98",
    "Weeks, 43",
    "Months, 6",
  })
  void shouldRefundLostFeesIfPeriodIsPassing(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime now = now(UTC);
    ClockUtil.setClock(fixed(Instant.ofEpochMilli(now.getMillis()), ZoneOffset.UTC));

    final DateTime lostDateTime = now.minus(period.timePeriod());
    assertTrue(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 656",
    "Hours, 6",
    "Days, 98",
    "Weeks, 43",
    "Months, 44",
  })
  void shouldNotRefundLostFeesIfPeriodHasPassed(String interval, int duration) {
    final Period period = from(duration, interval);
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withFeeRefundInterval(period);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    final DateTime lostDateTime = now(UTC).minus(period.timePeriod()).minusSeconds(1);
    assertFalse(lostItemPolicy.shouldRefundFees(lostDateTime));
  }

  @Test
  void shouldNotAgeItemToLostIfActualCostIsUsed() {
    final LostItemFeePolicyBuilder builder = new LostItemFeePolicyBuilder()
      .withItemAgedToLostAfterOverdue(minutes(1))
      .withActualCost(10.0);

    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(builder.create());

    assertFalse(lostItemPolicy.canAgeLoanToLost(false, now(UTC)));
  }

  @ParameterizedTest
  @CsvSource(value = {
    "Minutes, 0",
    "Hours, 0",
    "Days, 0",
    "Weeks, 0",
    "Months, 0",
    "null, null"
  }, nullValues = {"null"})
  public void canCalculateBillingDateWhenPatronIsBilledImmediatelyForNotRecalledItem(
    String interval, Integer duration) {

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
  void canCalculateBillingDateWhenPatronBillingIsDelayedForNotRecalledItem() {
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
  void shouldUseRecallIntervalForBillingDateWhenItemRecalled() {
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
  void shouldNotUseRecallIntervalForNotRecalledItem() {
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
  void ageToLostProcessingFeeIsNotChargeableIfAmountIsSetButFlagIsFalse() {
    final LostItemPolicy lostItemPolicy = LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .doNotChargeProcessingFeeWhenAgedToLost()
        .chargeProcessingFeeWhenDeclaredLost(10.0)
        .create());

    assertFalse(lostItemPolicy.getAgeToLostProcessingFee().isChargeable());
    assertTrue(lostItemPolicy.getDeclareLostProcessingFee().isChargeable());
  }

  @Test
  void ageToLostProcessingFeeIsChargeableEvenIfDeclaredLostFlagIsFalse() {
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
