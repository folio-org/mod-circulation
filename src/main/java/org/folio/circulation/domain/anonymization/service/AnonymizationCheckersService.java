package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.checkers.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.checkers.AnonymizeLoansImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checkers.AnonymizeLoansWithFeeFinesImmediatelyChecker;
import org.folio.circulation.domain.anonymization.checkers.FeesAndFinesClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checkers.LoanClosePeriodChecker;
import org.folio.circulation.domain.anonymization.checkers.NeverAnonymizeLoansChecker;
import org.folio.circulation.domain.anonymization.checkers.NeverAnonymizeLoansWithFeeFinesChecker;
import org.folio.circulation.domain.anonymization.checkers.NoAssociatedFeesAndFinesChecker;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfigurationForTenant;

public class AnonymizationCheckersService {

  private final LoanAnonymizationConfigurationForTenant config;

  public AnonymizationCheckersService(LoanAnonymizationConfigurationForTenant config) {
    this.config = config;
  }

  public AnonymizationCheckersService() {
    this(null);
  }

  public Map<String, List<String>> segregateLoans(Collection<Loan> loans) {
    return loans.stream()
      .collect(Collectors.groupingBy(l -> {
        AnonymizationChecker checker = getAnonymizationCheckers(l);
        if (!checker.canBeAnonymized(l)) {
          return checker.getReason();
        } else {
          return CAN_BE_ANONYMIZED_KEY;
        }
      }, Collectors.mapping(Loan::getId, Collectors.toList())));
  }

  private AnonymizationChecker getAnonymizationCheckers(Loan loan) {
    if (config == null) {
      return getManualAnonymizationChecker();
    }
    if (loan.hasAssociatedFeesAndFines() && config.treatLoansWithFeesAndFinesDifferently()) {
      return getFeesAndFinesCheckersFromLoanHistory();
    } else {
      return getClosedLoansCheckersFromLoanHistory();
    }
  }

  private AnonymizationChecker getManualAnonymizationChecker() {
    return new NoAssociatedFeesAndFinesChecker();
  }

  private AnonymizationChecker getClosedLoansCheckersFromLoanHistory() {
    AnonymizationChecker checker = null;
    if (config == null) {
      return getManualAnonymizationChecker();
    }

    switch (config.getLoanClosingType()) {
    case IMMEDIATELY:
      checker = new AnonymizeLoansImmediatelyChecker();
      break;
    case INTERVAL:
      checker = new LoanClosePeriodChecker(config.getLoanClosePeriod());
      break;
    case UNKNOWN:
    case NEVER:
      checker = new NeverAnonymizeLoansChecker();
    }

    return checker;
  }

  private AnonymizationChecker getFeesAndFinesCheckersFromLoanHistory() {
    AnonymizationChecker checker = null;

    switch (config.getFeesAndFinesClosingType()) {
    case IMMEDIATELY:
      checker = new AnonymizeLoansWithFeeFinesImmediatelyChecker();
      break;
    case INTERVAL:
      checker = new FeesAndFinesClosePeriodChecker(config.getFeeFineClosePeriod());
      break;
    case UNKNOWN:
    case NEVER:
      checker = new NeverAnonymizeLoansWithFeeFinesChecker();
    }

    return checker;
  }
}