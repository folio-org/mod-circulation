package org.folio.circulation.domain.anonymization;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfigurationForTenant;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersFacade;
import org.folio.circulation.domain.anonymization.service.LoanAnonymizationFinderService;
import org.folio.circulation.domain.anonymization.service.LoansForBorrowerFinder;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.support.Clients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanAnonymization {

  public static final int FETCH_LOANS_LIMIT = 5000;
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());
  private final Clients clients;
  private LoanAnonymizationFinderService loansFinderService;
  private AnonymizationCheckersFacade anonymizationCheckersFacade;

  public LoanAnonymization(Clients clients) {
    this.clients = clients;
  }

  public LoanAnonymizationService byUserId(String userId) {
    log.info("Initializing loan anonymization for borrower");

    loansFinderService = new LoansForBorrowerFinder(clients, userId);
    anonymizationCheckersFacade = new AnonymizationCheckersFacade();

    return new DefaultLoanAnonymizationService(clients, anonymizationCheckersFacade, loansFinderService);
  }

  public LoanAnonymizationService byCurrentTenant(
      LoanAnonymizationConfigurationForTenant config) {
    log.info("Initializing loan anonymization for current tenant");

    loansFinderService = new LoansForTenantFinder(clients);
    anonymizationCheckersFacade = new AnonymizationCheckersFacade(config);

    return new DefaultLoanAnonymizationService(clients, anonymizationCheckersFacade, loansFinderService);
  }
}
