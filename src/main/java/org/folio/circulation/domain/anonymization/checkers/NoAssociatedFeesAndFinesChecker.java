package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class NoAssociatedFeesAndFinesChecker extends DefaultAnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return !hasAssociatedFeesAndFines(loan);
  }

  @Override
  public String getReason() {
    return "haveAssociatedFeesAndFines";
  }
}