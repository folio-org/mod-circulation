package org.folio.circulation.domain.anonymization;

import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.List;

import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;
import org.folio.circulation.domain.anonymization.checks.FeesAndFinesClosedAnonymizationChecker;
import org.folio.circulation.support.Clients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanAnonymizationFacade {

  private final int fetchLoansLimit = 5000;
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());
  private final Clients clients;
  private final LoanAnonymizationService loanAnonymizationService;
  private final List<AnonymizationChecker> anonymizationCheckers =
      Collections.singletonList(new FeesAndFinesClosedAnonymizationChecker());
  private LoanAnonymizationFinderService loansFinderService;

  public LoanAnonymizationFacade(Clients clients) {
    this.clients = clients;
    loanAnonymizationService = new DefaultLoanAnonymizationService(this);
  }

  public LoanAnonymizationService byUserId(String userId) {
    log.info("Initializing loan anonymization for borrower {}", userId);

    loansFinderService = new LoansForBorrowerFinder(this, userId);
    return loanAnonymizationService;
  }

  public LoanAnonymizationService byCurrentTenant() {
    log.info("Initializing loan anonymization for current tenant");
    loansFinderService = new LoansForTenantFinder(this);
    return loanAnonymizationService;
  }

  Clients clients() {
    return clients;
  }

  List<AnonymizationChecker> anonymizationCheckers() {
    return anonymizationCheckers;
  }

  int getFetchLoansLimit() {
    return fetchLoansLimit;
  }

  LoanAnonymizationFinderService loansFinder() {
    return loansFinderService;
  }
}
