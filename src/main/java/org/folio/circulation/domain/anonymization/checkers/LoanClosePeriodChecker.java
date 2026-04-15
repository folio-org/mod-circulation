package org.folio.circulation.domain.anonymization.checkers;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.ZonedDateTime;

import org.folio.circulation.Clock;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;

public class LoanClosePeriodChecker implements AnonymizationChecker {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

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

  boolean itemReturnedEarlierThanPeriod(ZonedDateTime returnDate) {
    if (returnDate == null) {
      log.info("itemReturnedEarlierThanPeriod:: returnDate is null, cannot check if loan can be anonymized");
      return false;
    }

    return clock.now().isAfter(period.plusDate(returnDate));
  }
}
