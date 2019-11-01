package org.folio.circulation.domain.anonymization.checkers;

import org.folio.circulation.domain.Loan;

public interface AnonymizationChecker {

  boolean canBeAnonymized(Loan loan);

  String getReason();

}
