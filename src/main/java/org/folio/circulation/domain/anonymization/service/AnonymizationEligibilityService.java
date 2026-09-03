package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.Clock;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.AnonymizationEligibility;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.support.utils.ClockUtil;

/**
 * Segregates loans into "can be anonymized" and reason buckets for the
 * scheduled and manual paths. Timing lives in {@link AnonymizationEligibility};
 * this service orchestrates the short-circuit, the manual-path rule, and the
 * grouping.
 */
public class AnonymizationEligibilityService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LoanAnonymizationConfiguration config;
  private final Clock clock;

  public AnonymizationEligibilityService(LoanAnonymizationConfiguration config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  public AnonymizationEligibilityService() {
    this(null, ClockUtil::getZonedDateTime);
  }

  public boolean neverAnonymizeLoans() {
    log.debug("neverAnonymizeLoans:: checking if loans should never be anonymized");
    if (config == null) {
      return false;
    }
    boolean result = config.getLoanClosingType() == ClosingType.NEVER
      && !config.treatLoansWithFeesAndFinesDifferently();
    log.info("neverAnonymizeLoans:: result: {}", result);
    return result;
  }

  public Map<String, Set<String>> segregateLoans(Collection<Loan> loans) {
    log.info("segregateLoans:: segregating {} loans", loans.size());
    Map<String, Set<String>> result = loans.stream()
      .collect(Collectors.groupingBy(this::bucketFor,
        Collectors.mapping(Loan::getId, Collectors.toSet())));
    log.info("segregateLoans:: result: segregated into {} categories", result.size());
    return result;
  }

  private String bucketFor(Loan loan) {
    // Manual (on-demand) path: no tenant timing policy — only loans that carry
    // no fees/fines may be anonymized.
    if (config == null) {
      return loan.hasAssociatedFeesAndFines()
        ? "haveAssociatedFeesAndFines"
        : CAN_BE_ANONYMIZED_KEY;
    }

    return AnonymizationEligibility.isDue(loan, config, clock)
      ? CAN_BE_ANONYMIZED_KEY
      : AnonymizationEligibility.reason(loan, config);
  }
}
