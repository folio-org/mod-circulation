package org.folio.circulation.domain.anonymization;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.folio.circulation.Clock;
import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.support.utils.DateTimeUtil;

/**
 * Loan-anonymization eligibility timing. {@link #dueAt} gives the instant a
 * closed loan becomes eligible under a policy; {@link #isDue} and
 * {@link #reason} derive from it. The boundary is inclusive ({@code now >=
 * dueAt}), so a closed loan with no return date under an "immediately" policy
 * ({@code dueAt == now}) is eligible.
 */
public final class AnonymizationEligibility {

  private AnonymizationEligibility() {
  }

  /**
   * The instant this closed loan becomes eligible under the given policy, or
   * empty if it never does (never policy, or not yet computable because fees
   * are still open, or a missing anchor date). Returns empty for a null or
   * open loan — such loans are not candidates.
   */
  public static Optional<ZonedDateTime> dueAt(Loan loan,
    LoanAnonymizationConfiguration policy, Clock clock) {

    if (loan == null || policy == null || !loan.isClosed()) {
      return Optional.empty();
    }

    return isFeeSegment(loan, policy)
      ? feeDueAt(loan, policy, clock)
      : standardDueAt(loan, policy, clock);
  }

  /** True when the loan is due for anonymization at {@code clock.now()}. */
  public static boolean isDue(Loan loan, LoanAnonymizationConfiguration policy, Clock clock) {
    return dueAt(loan, policy, clock)
      .map(due -> !clock.now().isBefore(due))
      .orElse(false);
  }

  /** The label explaining why a not-due loan is retained under this policy. */
  public static String reason(Loan loan, LoanAnonymizationConfiguration policy) {
    if (isFeeSegment(loan, policy)) {
      switch (policy.getFeesAndFinesClosingType()) {
        case IMMEDIATELY: return "feesAndFinesOpen";
        case INTERVAL:    return "intervalAfterFeesAndFinesCloseNotPassed";
        default:          return "neverAnonymizeLoansWithFeesAndFines";
      }
    }
    switch (policy.getLoanClosingType()) {
      case IMMEDIATELY: return "anonymizeImmediately";
      case INTERVAL:    return "loanClosedPeriodNotPassed";
      default:          return "neverAnonymizeLoans";
    }
  }

  /**
   * The fee/fine segment governs when the loan carries fees/fines and the
   * policy treats them differently; otherwise the standard segment.
   * Payment-method exceptions ({@code loanExceptions}) are not read by the
   * backend and have no effect.
   */
  private static boolean isFeeSegment(Loan loan, LoanAnonymizationConfiguration policy) {
    return loan.hasAssociatedFeesAndFines()
      && policy.treatLoansWithFeesAndFinesDifferently();
  }

  private static Optional<ZonedDateTime> standardDueAt(Loan loan,
    LoanAnonymizationConfiguration policy, Clock clock) {

    final ZonedDateTime returnDate = loan.getSystemReturnDate();

    switch (policy.getLoanClosingType()) {
      case IMMEDIATELY:
        // Closed loans are due at once; use the return date when known, else now.
        return Optional.of(returnDate != null ? returnDate : clock.now());
      case INTERVAL:
        // Without a return date the interval has no anchor (never eligible).
        return Optional.ofNullable(returnDate)
          .map(date -> policy.getLoanClosePeriod().plusDate(date));
      case NEVER, UNKNOWN:
      default:
        return Optional.empty();
    }
  }

  private static Optional<ZonedDateTime> feeDueAt(Loan loan,
    LoanAnonymizationConfiguration policy, Clock clock) {

    if (!loan.allFeesAndFinesClosed()) {
      // Not computable while fees are open; becomes eligible once they close.
      return Optional.empty();
    }

    final Optional<ZonedDateTime> latestClose = loan.getAccounts().stream()
      .map(Account::getClosedDate)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .max(DateTimeUtil::compareToMillis);

    switch (policy.getFeesAndFinesClosingType()) {
      case IMMEDIATELY:
        return Optional.of(latestClose.orElseGet(clock::now));
      case INTERVAL:
        return latestClose.map(date -> policy.getFeeFineClosePeriod().plusDate(date));
      case NEVER, UNKNOWN:
      default:
        return Optional.empty();
    }
  }
}
