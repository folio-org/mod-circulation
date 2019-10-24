package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class NeverAnonymizeLoansWithFeeFinesChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return false;
  }

  @Override
  public String getReason() { return "neverAnonymizeLoansWithFeesAndFines"; }
}