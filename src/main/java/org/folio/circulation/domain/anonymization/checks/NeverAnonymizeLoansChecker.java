package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;

public class NeverAnonymizeLoansChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return false;
  }

  @Override
  public String getReason() { return "neverAnonymizeLoans"; }
}