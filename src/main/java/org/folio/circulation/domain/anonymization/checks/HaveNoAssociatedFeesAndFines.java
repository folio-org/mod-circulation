package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;

public class HaveNoAssociatedFeesAndFines implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.getAccounts().isEmpty();
  }

  @Override
  public String getReason() {
    return "haveAssociatedFeesAndFines";
  }
}