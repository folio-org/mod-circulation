package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.Clock;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.joda.time.DateTime;

public class LoanClosePeriodChecker implements AnonymizationChecker {
  private final Period period;
  private final Clock clock;

  public LoanClosePeriodChecker(Period period, Clock clock) {
    this.period = period;
    this.clock = clock;
  }

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.isClosed() && itemReturnedEarlierThanPeriod(loan.getSystemReturnDate());
  }

  @Override
  public String getReason() {
    return "loanClosedPeriodNotPassed";
  }

  boolean itemReturnedEarlierThanPeriod(DateTime startDate) {
    if (startDate == null) {
      return false;
    }

    return clock.now().isAfter(startDate.plus(period.timePeriod()));
  }
}
