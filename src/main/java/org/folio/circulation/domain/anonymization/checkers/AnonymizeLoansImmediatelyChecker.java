package org.folio.circulation.domain.anonymization.checkers;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.circulation.domain.Loan;

public class AnonymizeLoansImmediatelyChecker implements AnonymizationChecker {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public boolean canBeAnonymized(Loan loan) {
    return loan.isClosed();
  }

  @Override
  public String getReason() { return "anonymizeImmediately"; }
}
