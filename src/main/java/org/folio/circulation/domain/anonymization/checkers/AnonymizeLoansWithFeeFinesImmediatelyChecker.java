package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class AnonymizeLoansWithFeeFinesImmediatelyChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.allFeesAndFinesClosed();
  }

  @Override
  public String getReason() { return "feesAndFinesOpen"; }
}