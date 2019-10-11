package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;

public class NeverAnonymizeLoansWithFeeFinesChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return !hasAssociatedFeesAndFines(loan);
  }

  @Override
  public String getReason() { return "neverAnonymizeLoansWithFeesAndFines"; }
}