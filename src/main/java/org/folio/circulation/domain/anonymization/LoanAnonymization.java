package org.folio.circulation.domain.anonymization;

import org.folio.circulation.support.Clients;

public class LoanAnonymization {
  public static final int FETCH_LOANS_LIMIT = 5000;

  private LoanAnonymization() {
  }

  public static LoanAnonymizationService newLoanAnonymizationService(Clients clients) {

    return new CheckingLoanAnonymizationService(clients);
  }
}
