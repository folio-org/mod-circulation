package org.folio.circulation.domain.anonymization.checkers;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.circulation.domain.Loan;

public class NoAssociatedFeesAndFinesChecker implements AnonymizationChecker {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  @Override
  public boolean canBeAnonymized(Loan loan) {
    log.debug("canBeAnonymized:: checking if loan {} has associated fees/fines", loan != null ? loan.getId() : "null");
    boolean result = loan.isClosed() && !loan.hasAssociatedFeesAndFines();
    log.debug("canBeAnonymized:: loan {}: {}", loan != null ? loan.getId() : "null", result ? "can be anonymized (no fees/fines)" : "cannot be anonymized (has fees/fines)");
    return result;
  }

  @Override
  public String getReason() {
    return "haveAssociatedFeesAndFines";
  }
}
