package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;

public class HaveNoAssociatedFeesAndFinesChecker implements
    AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return !hasAssociatedFeesAndFines(loan);
  }

  @Override
  public String getReason() {
    return "haveAssociatedFeesAndFines";
  }
}