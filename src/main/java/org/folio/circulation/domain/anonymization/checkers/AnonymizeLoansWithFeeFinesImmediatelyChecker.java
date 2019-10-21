package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public class AnonymizeLoansWithFeeFinesImmediatelyChecker extends DefaultAnonymizationChecker {

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return allFeesAndFinesClosed(loan);
  }

  @Override
  public String getReason() { return "feesAndFinesOpen"; }
}