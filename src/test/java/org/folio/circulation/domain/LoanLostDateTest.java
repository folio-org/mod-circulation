package org.folio.circulation.domain;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static org.hamcrest.Matchers.allOf;
import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import api.support.builders.LoanBuilder;

public class LoanLostDateTest {
  @Test
  public void declaredLostDateReturnedWhenSet() {
    final var declaredLostDate = now(UTC);
    final var loan = new LoanBuilder().asDomainObject()
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(declaredLostDate.isEqual(loan.getLostDate()));
  }

  @Test
  public void agedToLostDateReturnedWhenSet() {
    final var agedToLostDate = now(UTC);
    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate);

    assertTrue(agedToLostDate.isEqual(loan.getLostDate()));
  }

  @Test
  public void declaredLostDateReturnedWhenIsAfterAgedToLostDate() {
    final var agedToLostDate = now(UTC).minusDays(2);
    final var declaredLostDate = now(UTC);

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate)
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(declaredLostDate.isEqual(loan.getLostDate()));
    // make sure properties are not cleared
    assertThat(loan.asJson(), allOf(
      hasJsonPath("declaredLostDate", declaredLostDate.toString()),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", agedToLostDate.toString())
    ));
  }

  @Test
  public void agedToLostDateReturnedWhenIsAfterDeclaredLostDate() {
    final var declaredLostDate = now(UTC).minusDays(3);
    final var agedToLostDate = now(UTC);

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(agedToLostDate)
      .declareItemLost("Lost", declaredLostDate);

    assertTrue(agedToLostDate.isEqual(loan.getLostDate()));
    // make sure properties are not cleared
    assertThat(loan.asJson(), allOf(
      hasJsonPath("declaredLostDate", declaredLostDate.toString()),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", agedToLostDate.toString())
    ));
  }

  @Test
  public void lostDateIsNotNullWhenBothLostDatesAreEqual() {
    final var lostDate = now(UTC);

    final var loan = new LoanBuilder().asDomainObject()
      .ageOverdueItemToLost(lostDate)
      .declareItemLost("Lost", lostDate);

    assertTrue(lostDate.isEqual(loan.getLostDate()));
  }

  @Test
  public void lostDateIsNullWhenLoanWasNeverLost() {
    final var loan = new LoanBuilder().asDomainObject();

    assertNull(loan.getLostDate());
  }
}
