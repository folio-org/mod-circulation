package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.Loan;

public class FeesAndFinesClosedAnonymizationCheck implements AnonymizationCheck  {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.getAccounts()
      .stream()
      .allMatch(Account::isClosed);

  }

  @Override
  public String getReason() {
    return "openfeesandfines";
  }
}