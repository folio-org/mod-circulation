package org.folio.circulation.domain;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static api.support.matchers.TextDateTimeMatcher.isEquivalentTo;
import static org.folio.circulation.domain.ItemStatus.AGED_TO_LOST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;

import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.domain.policy.lostitem.LostItemPolicy;
import org.joda.time.DateTime;
import org.junit.Test;
import org.junit.runner.RunWith;

import api.support.builders.ItemBuilder;
import api.support.builders.LoanBuilder;
import api.support.builders.LostItemFeePolicyBuilder;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;

@RunWith(JUnitParamsRunner.class)
public class LoanTest {
  private static final String LOST_ITEM_HAS_BEEN_BILLED = "agedToLostDelayedBilling.lostItemHasBeenBilled";
  private static final String DATE_LOST_ITEM_SHOULD_BE_BILLED = "agedToLostDelayedBilling.dateLostItemShouldBeBilled";

  @Test
  @Parameters( {
    "Minutes, 0",
    "Hours, 0",
    "Days, 0",
    "Weeks, 0",
    "Months, 0",
    "null, null"
  })
  public void canAddBillingDateWhenPatronIsBilledImmediately(
    @Nullable String interval, @Nullable Integer duration) {

    final Period ageToLostAfterPeriod = Period.weeks(6);
    final Period billPatronInterval = duration == null && interval == null
      ? null : Period.from(duration, interval);

    final DateTime loanDueDate = DateTime.now();
    final DateTime expectedBillingDate = loanDueDate.plus(ageToLostAfterPeriod.timePeriod());

    final Item item = Item.from(new ItemBuilder().checkOut().create());
    final LostItemPolicy chargePatronImmediatelyPolicy =  LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
      .withPatronBilledAfterAgedLost(billPatronInterval)
      .withItemAgedToLostAfterOverdue(ageToLostAfterPeriod)
      .withSetCost(10.0)
      .doNotChargeProcessingFee()
      .create());

    final Loan loan = Loan.from(new LoanBuilder().withDueDate(loanDueDate).create())
      .withLostItemPolicy(chargePatronImmediatelyPolicy)
      .withItem(item);

    loan.ageOverdueItemToLost();

    assertThat(loan.getItem().getStatus(), is(AGED_TO_LOST));
    assertThat(loan.asJson(), allOf(hasJsonPath(LOST_ITEM_HAS_BEEN_BILLED, false),
      hasJsonPath(DATE_LOST_ITEM_SHOULD_BE_BILLED, isEquivalentTo(expectedBillingDate))));
  }

  @Test
  public void canAddBillingDateWhenPatronBillingIsDelayed() {
    final Period ageToLostAfterPeriod = Period.weeks(6);
    final Period billPatronAfterPeriod = Period.weeks(1);
    final DateTime loanDueDate = DateTime.now();
    final DateTime expectedBillingDate = loanDueDate.plus(ageToLostAfterPeriod.timePeriod())
      .plus(billPatronAfterPeriod.timePeriod());

    final Item item = Item.from(new ItemBuilder().checkOut().create());
    final LostItemPolicy chargePatronImmediatelyPolicy =  LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterAgedLost(billPatronAfterPeriod)
        .withItemAgedToLostAfterOverdue(ageToLostAfterPeriod)
        .withSetCost(10.0)
        .doNotChargeProcessingFee()
        .create());

    final Loan loan = Loan.from(new LoanBuilder().withDueDate(loanDueDate).create())
      .withLostItemPolicy(chargePatronImmediatelyPolicy)
      .withItem(item);

    loan.ageOverdueItemToLost();

    assertThat(loan.getItem().getStatus(), is(AGED_TO_LOST));
    assertThat(loan.asJson(), allOf(hasJsonPath(LOST_ITEM_HAS_BEEN_BILLED, false),
      hasJsonPath(DATE_LOST_ITEM_SHOULD_BE_BILLED, isEquivalentTo(expectedBillingDate))));
  }

  @Test
  public void cannotAddBillingDateWhenImmediateBillingAndNoFeesToCharge() {
    final Period ageToLostAfterPeriod = Period.weeks(6);
    final DateTime loanDueDate = DateTime.now();

    final Item item = Item.from(new ItemBuilder().checkOut().create());
    final LostItemPolicy chargePatronImmediatelyPolicy =  LostItemPolicy.from(
      new LostItemFeePolicyBuilder()
        .withPatronBilledAfterAgedLost(null)
        .withItemAgedToLostAfterOverdue(ageToLostAfterPeriod)
        .withNoChargeAmountItem()
        .doNotChargeProcessingFee()
        .create());

    final Loan loan = Loan.from(new LoanBuilder().withDueDate(loanDueDate).create())
      .withLostItemPolicy(chargePatronImmediatelyPolicy)
      .withItem(item);

    loan.ageOverdueItemToLost();

    assertThat(loan.getItem().getStatus(), is(AGED_TO_LOST));
    assertFalse(loan.asJson().containsKey("agedToLostDelayedBilling"));
  }
}
