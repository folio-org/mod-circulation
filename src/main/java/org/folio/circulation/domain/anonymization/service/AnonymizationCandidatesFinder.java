package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.lang.invoke.MethodHandles;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.Clock;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.policy.Period;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.support.results.Result;

/**
 * Finds the loans of one preference group for the scheduled anonymization, used when
 * user-specific loan anonymization is enabled. Loans of users who chose "never" are never
 * returned by storage.
 */
public class AnonymizationCandidatesFinder extends DefaultLoansFinder {
  public static final String AS_EARLY_AS_POSSIBLE = "asEarlyAsPossible";
  public static final String LIBRARY_POLICY = "libraryPolicy";
  private static final int MONTHS_MARGIN_DAYS = 3;

  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final LoanRepository loanRepository;
  private final Clock clock;

  public AnonymizationCandidatesFinder(LoanRepository loanRepository,
    AccountRepository accountRepository, Clock clock) {

    super(accountRepository);
    this.loanRepository = loanRepository;
    this.clock = clock;
  }

  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize(String preference,
    LoanAnonymizationConfiguration config, int limit) {

    if (limit < 1) {
      log.info("findLoansToAnonymize:: no loans left to check for {}", preference);
      return ofAsync(List.of());
    }

    ZonedDateTime returnedBefore = returnedBefore(config, clock.now());
    log.info("findLoansToAnonymize:: searching for up to {} loans of {} returned before {}",
      limit, preference, returnedBefore);

    return loanRepository.findAnonymizationCandidates(preference, returnedBefore, limit)
      .thenCompose(this::fetchAdditionalLoanInfo);
  }

  /**
   * Date filter for storage, null for none: storage only returns loans with a system return
   * date before it. The checkers still decide each loan, so the filter may let through loans
   * that are not due yet, but a loan it holds back is anonymized late.
   * <p>
   * The checker anonymizes a loan when now is after return date + period. For minutes, hours,
   * days and weeks that is exactly: return date before now - period (the clock is UTC).
   * <p>
   * Months can't be reversed exactly, because adding months clamps to the end of the month:
   * January 28, 29, 30 and 31 plus one month are all February 28. On February 28 at noon, a
   * loan returned on January 31 is due, but now - 1 month is January 28 at noon, so without a
   * margin storage would hold that loan back for up to three more days. The margin of three
   * days lets these loans through; the few loans in the margin that are not due yet are
   * rejected by the checker.
   */
  static ZonedDateTime returnedBefore(LoanAnonymizationConfiguration config, ZonedDateTime now) {
    ClosingType feesAndFinesClosingType = config.getFeesAndFinesClosingType();
    if (config.treatLoansWithFeesAndFinesDifferently()
      && (feesAndFinesClosingType == ClosingType.IMMEDIATELY
        || feesAndFinesClosingType == ClosingType.INTERVAL)) {

      // Loans with fees/fines are timed from the date the last fee/fine was closed
      return null;
    }

    Period period = config.getLoanClosePeriod();
    if (config.getLoanClosingType() != ClosingType.INTERVAL || period == null
      || !period.isValid()) {

      return null;
    }

    ZonedDateTime cutoff = period.minusDate(now);
    return period.isMonths() ? cutoff.plusDays(MONTHS_MARGIN_DAYS) : cutoff;
  }
}
