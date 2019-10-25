package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
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

  public AnonymizationCheckersService(
      LoanAnonymizationConfigurationForTenant config) {
    this.config = config;
  }

  public AnonymizationCheckersService() {
    this(null);
  }

  public HashSetValuedHashMap<String, String> segregateLoans(Collection<Loan> loans) {
    HashSetValuedHashMap<String, String> multiMap = new HashSetValuedHashMap<>();
    for (Loan loan : loans) {
      boolean loanCanBeAnonymized = true;
      List<AnonymizationChecker> anonymizationCheckers =
          getAnonymizationCheckers(loan);
      for (AnonymizationChecker checker : anonymizationCheckers) {
        if (!checker.canBeAnonymized(loan)) {
          multiMap.put(checker.getReason(), loan.getId());
          loanCanBeAnonymized = false;
        }
      }
      if (loanCanBeAnonymized)
        multiMap.put(CAN_BE_ANONYMIZED_KEY, loan.getId());
    }
    return multiMap;
  }

  private List<AnonymizationChecker> getAnonymizationCheckers(Loan loan) {
    if (config == null) {
      return getDefaultCheckers();
    }
    if (loan.hasAssociatedFeesAndFines() && config.treatLoansWithFeesAndFinesDifferently()) {
      return getFeesAndFinesCheckersFromLoanHistory();
    } else {
      return getClosedLoansCheckersFromLoanHistory();
    }
  }

  private List<AnonymizationChecker> getDefaultCheckers() {
    return Collections.singletonList(new NoAssociatedFeesAndFinesChecker());
  }

  private List<AnonymizationChecker> getClosedLoansCheckersFromLoanHistory() {
    List<AnonymizationChecker> result = new ArrayList<>();
    if (config == null) {
      return result;
    }

    switch (config.getLoanClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new LoanClosePeriodChecker(
          config.getLoanClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansChecker());
      break;
    default:
      return result;
    }

    return result;
  }

  private List<AnonymizationChecker> getFeesAndFinesCheckersFromLoanHistory() {
    List<AnonymizationChecker> result = new ArrayList<>();
    if (config == null || !config
        .treatLoansWithFeesAndFinesDifferently()) {
      return result;
    }
    switch (config.getFeesAndFinesClosingType()) {
    case IMMEDIATELY:
      result.add(new AnonymizeLoansWithFeeFinesImmediatelyChecker());
      break;
    case INTERVAL:
      result.add(new FeesAndFinesClosePeriodChecker(
          config.getFeeFineClosePeriod()));
      break;
    case UNKNOWN:
    case NEVER:
      result.add(new NeverAnonymizeLoansWithFeeFinesChecker());
      break;
    default:
      return result;
    }

    return result;
  }
}