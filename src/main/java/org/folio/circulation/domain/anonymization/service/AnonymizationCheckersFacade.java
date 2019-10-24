package org.folio.circulation.domain.anonymization.service;

import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.Collection;
import java.util.List;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.checkers.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfigurationForTenant;

public class AnonymizationCheckersFacade {

  private final LoanAnonymizationConfigurationForTenant config;
  private AnonymizationCheckersProvider checkersProvider;

  public AnonymizationCheckersFacade(
      LoanAnonymizationConfigurationForTenant config) {
    this.config = config;
    checkersProvider = new AnonymizationCheckersProvider(config);
  }

  public AnonymizationCheckersFacade() {
    this(null);
    checkersProvider = new AnonymizationCheckersProvider();
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
      return checkersProvider.getGeneralCheckers();
    }
    if (!loan.getAccounts().isEmpty() && config.treatLoansWithFeesAndFinesDifferently()) {
      return checkersProvider.getFeesAndFinesCheckers();
    } else {
      return checkersProvider.getClosedLoansCheckers();
    }
  }
}