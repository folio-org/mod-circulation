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
    log.debug("canBeAnonymized:: checking loan {} for anonymization eligibility", loan != null ? loan.getId() : "null");
    boolean result = loan.isClosed() && itemReturnedEarlierThanPeriod(loan.getSystemReturnDate());
    log.debug("canBeAnonymized:: loan {}: {}", loan != null ? loan.getId() : "null", result ? "can be anonymized" : "cannot be anonymized (reason: " + getReason() + ")");
    return result;
  }

  @Override
  public String getReason() {
    return "loanClosedPeriodNotPassed";
  }

  boolean itemReturnedEarlierThanPeriod(ZonedDateTime returnDate) {
    if (returnDate == null) {
      return false;
    }

    return clock.now().isAfter(period.plusDate(returnDate));
  }
}
