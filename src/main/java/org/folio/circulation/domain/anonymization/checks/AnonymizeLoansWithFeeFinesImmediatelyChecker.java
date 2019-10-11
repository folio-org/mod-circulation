package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;

public class AnonymizeLoansWithFeeFinesImmediatelyChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return allFeesAndFinesClosed(loan) && loan.isClosed();
  }

  @Override
  public String getReason() { return "anonymizeLoansWithFeeFinesImmediately"; }
}