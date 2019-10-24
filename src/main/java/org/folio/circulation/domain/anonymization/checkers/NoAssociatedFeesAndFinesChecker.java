package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class NoAssociatedFeesAndFinesChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return !loan.hasAssociatedFeesAndFines();
  }

  @Override
  public String getReason() {
    return "haveAssociatedFeesAndFines";
  }
}