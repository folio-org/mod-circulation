package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;

public class AnonymizationCheckersService {

  private final LoanAnonymizationConfiguration config;

  private final AnonymizationChecker manualAnonymizationChecker;
  private AnonymizationChecker feesAndFinesCheckersFromLoanHistory;
  private AnonymizationChecker closedLoansCheckersFromLoanHistory;


  public AnonymizationCheckersService(LoanAnonymizationConfiguration config) {
    this.config = config;
    if ( config != null) {
      feesAndFinesCheckersFromLoanHistory = getFeesAndFinesCheckersFromLoanHistory();
      closedLoansCheckersFromLoanHistory = getClosedLoansCheckersFromLoanHistory();
    }
    manualAnonymizationChecker = getManualAnonymizationChecker();
  }

  public AnonymizationCheckersService() {
    this(null);
  }

  public Map<String, Set<String>> segregateLoans(Collection<Loan> loans) {
    return loans.stream()
      .collect(Collectors.groupingBy(applyCheckersForLoanAndLoanHistoryConfig(),
        Collectors.mapping(Loan::getId, Collectors.toSet())));
  }

  private Function<Loan, String> applyCheckersForLoanAndLoanHistoryConfig() {
    return loan -> {
      AnonymizationChecker checker;
      if (config == null) {
        checker = manualAnonymizationChecker;
      } else if (loan.hasAssociatedFeesAndFines() && config.treatLoansWithFeesAndFinesDifferently()) {
        checker = feesAndFinesCheckersFromLoanHistory;
      } else {
        checker = closedLoansCheckersFromLoanHistory;
      }

      if (!checker.canBeAnonymized(loan)) {
        return checker.getReason();
      } else {
        return CAN_BE_ANONYMIZED_KEY;
      }
    };
  }

  private AnonymizationChecker getManualAnonymizationChecker() {
    return new NoAssociatedFeesAndFinesChecker();
  }

  private AnonymizationChecker getClosedLoansCheckersFromLoanHistory() {
    AnonymizationChecker checker = null;

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
