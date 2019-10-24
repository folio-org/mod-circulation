package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class AnonymizeLoansImmediatelyChecker implements AnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.isClosed();
  }

  @Override
  public String getReason() { return "anonymizeImmediately"; }
}