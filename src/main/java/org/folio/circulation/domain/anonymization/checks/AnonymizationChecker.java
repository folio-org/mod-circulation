package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;

public interface AnonymizationChecker {

  boolean canBeAnonymized(Loan loan);

  String getReason();

  default boolean hasAssociatedFeesAndFines(Loan loan) {
    return !loan.getAccounts().isEmpty();
  }

  default boolean allFeesAndFinesClosed(Loan loan) {
    return loan.getAccounts().stream().allMatch(Account::isClosed);
  }
}
