package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.ClockUtil;
import org.joda.time.DateTime;

public class LoanClosePeriodChecker implements AnonymizationChecker {
  private final Period period;

  public LoanClosePeriodChecker(Period period) {
    this.period = period;
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
    return startDate != null && ClockUtil.getDateTime()
      .isAfter(startDate.plus(period.timePeriod()));
  }
}
