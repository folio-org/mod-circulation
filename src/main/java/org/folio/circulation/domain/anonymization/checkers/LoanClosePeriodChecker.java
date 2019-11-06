package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;

public class LoanClosePeriodChecker extends TimePeriodChecker {

  public LoanClosePeriodChecker(Period period) {
    super(period);
  }

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.isClosed() && checkTimePeriodPassed(loan.getSystemReturnDate());
  }

  @Override
  public String getReason() {
    return "loanClosedPeriodNotPassed";
  }
}