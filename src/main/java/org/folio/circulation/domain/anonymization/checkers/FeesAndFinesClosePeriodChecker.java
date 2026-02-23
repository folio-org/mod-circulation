package org.folio.circulation.domain.anonymization.checkers;
import java.lang.invoke.MethodHandles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.folio.circulation.Clock;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.support.utils.DateTimeUtil;
public class FeesAndFinesClosePeriodChecker implements AnonymizationChecker {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final Period period;
  private final Clock clock;
  public FeesAndFinesClosePeriodChecker(Period period, Clock clock) {
    this.period = period;
    this.clock = clock;
  }
  @Override
  public boolean canBeAnonymized(Loan loan) {
    log.debug("canBeAnonymized:: checking loan {} with fees/fines for anonymization", loan != null ? loan.getId() : "null");
    boolean result = loan.isClosed() && allFeesAndFinesClosedEarlierThanPeriod(loan);
    log.debug("canBeAnonymized:: loan {}: {}", loan != null ? loan.getId() : "null", result ? "can be anonymized" : "cannot be anonymized");
    return result;
  }
  @Override
  public String getReason() {
    return "intervalAfterFeesAndFinesCloseNotPassed";
  }
  boolean allFeesAndFinesClosedEarlierThanPeriod(Loan loan) {
    return findLatestAccountCloseDate(loan)
      .map(this::latestAccountClosedEarlierThanPeriod)
      .orElse(false);
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