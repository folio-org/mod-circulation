package org.folio.circulation.domain.anonymization.checks;

import org.folio.circulation.domain.Loan;

public interface AnonymizationCheck {

  boolean canBeAnonymized(Loan loan);

  String getReason();
}
