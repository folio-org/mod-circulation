package org.folio.circulation.domain;

import static api.support.matchers.JsonObjectMatcher.hasJsonPath;
import static java.time.ZoneOffset.UTC;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;
import static org.folio.circulation.support.utils.DateTimeUtil.isSameMillis;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;

import org.folio.circulation.support.utils.ClockUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import api.support.builders.LoanBuilder;

class LoanLostDateTest {

  @BeforeEach
  public void beforeEach() {
    ClockUtil.setClock(Clock.fixed(Instant.parse("2020-10-20T10:20:10.000Z"), UTC));
  }

  @AfterEach
  public void afterEach() {
    // The clock must be reset after each test.
    ClockUtil.setDefaultClock();
  }

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
      hasJsonPath("declaredLostDate", formatDateTime(declaredLostDate)),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", formatDateTime(agedToLostDate))
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
      hasJsonPath("declaredLostDate", formatDateTime(declaredLostDate)),
      hasJsonPath("agedToLostDelayedBilling.agedToLostDate", formatDateTime(agedToLostDate))
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
