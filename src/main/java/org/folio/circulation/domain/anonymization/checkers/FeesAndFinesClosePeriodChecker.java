package org.folio.circulation.domain.anonymization.checkers;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.folio.circulation.Clock;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.DateTimeUtil;

public class FeesAndFinesClosePeriodChecker implements AnonymizationChecker {
  private final Period period;
  private final Clock clock;

  public FeesAndFinesClosePeriodChecker(Period period, Clock clock) {
    this.period = period;
    this.clock = clock;
  }

  @Override
  public boolean canBeAnonymized(Loan loan) {
    if (!loan.allFeesAndFinesClosed()) {
      return false;
    }

    return findLatestAccountCloseDate(loan)
      .map(this::latestAccountClosedEarlierThanPeriod)
      .orElse(false);
  }

  @Override
  public String getReason() {
    return "intervalAfterFeesAndFinesCloseNotPassed";
  }

  private Optional<ZonedDateTime> findLatestAccountCloseDate(Loan loan) {
    return loan.getAccounts()
      .stream()
      .map(Account::getClosedDate)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .max(DateTimeUtil::compareToMillis);
  }

  boolean latestAccountClosedEarlierThanPeriod(ZonedDateTime lastAccountClosed) {
    return clock.now().isAfter(period.plusDate(lastAccountClosed));
  }
}
