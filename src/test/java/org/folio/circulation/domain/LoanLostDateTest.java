package org.folio.circulation.domain;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;

class LoanLostDateTest {
  @Test
  void declaredLostDateReturnedWhenSet() {
    final var declaredLostDate = ClockUtil.getZonedDateTime();
    final var loan = new LoanBuilder().asDomainObject()
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(isSameMillis(declaredLostDate, loan.getLostDate()));
  }

  @Test
  void agedToLostDateReturnedWhenSet() {
    final var agedToLostDate = ClockUtil.getZonedDateTime();
    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate);

    assertTrue(isSameMillis(agedToLostDate, loan.getLostDate()));
  }

  @Test
  void declaredLostDateReturnedWhenIsAfterAgedToLostDate() {
    final var agedToLostDate = ClockUtil.getZonedDateTime().minusDays(2);
    final var declaredLostDate = ClockUtil.getZonedDateTime();

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate)
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(isSameMillis(declaredLostDate, loan.getLostDate()));
    // make sure properties are not cleared
    assertThat(loan.asJson(), allOf(
      hasJsonPath("declaredLostDate", declaredLostDate.toString()),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", agedToLostDate.toString())
    ));
  }

  @Test
  void agedToLostDateReturnedWhenIsAfterDeclaredLostDate() {
    final var declaredLostDate = ClockUtil.getZonedDateTime().minusDays(3);
    final var agedToLostDate = ClockUtil.getZonedDateTime();

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate)
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(isSameMillis(agedToLostDate, loan.getLostDate()));
    // make sure properties are not cleared
    assertThat(loan.asJson(), allOf(
      hasJsonPath("declaredLostDate", declaredLostDate.toString()),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", agedToLostDate.toString())
    ));
  }

  @Test
  void lostDateIsNotNullWhenBothLostDatesAreEqual() {
    final var lostDate = ClockUtil.getZonedDateTime();

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(lostDate)
      .declareItemLost("Lost", lostDate);

    assertTrue(isSameMillis(lostDate, loan.getLostDate()));
  }

  @Test
  void lostDateIsNullWhenLoanWasNeverLost() {
    final var loan = new LoanBuilder().asDomainObject();

    assertNull(loan.getLostDate());
  }
}
