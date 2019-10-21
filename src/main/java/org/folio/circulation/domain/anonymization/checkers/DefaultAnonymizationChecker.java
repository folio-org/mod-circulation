package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;

abstract class DefaultAnonymizationChecker implements AnonymizationChecker {

  boolean hasAssociatedFeesAndFines(Loan loan) {
    return !loan.getAccounts().isEmpty();
  }

  boolean allFeesAndFinesClosed(Loan loan) {
    return loan.getAccounts().stream().allMatch(Account::isClosed);
  }
}