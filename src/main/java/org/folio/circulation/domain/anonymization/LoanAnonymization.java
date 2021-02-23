package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.anonymization.service.LoanAnonymizationFinderService;
import org.folio.circulation.domain.anonymization.service.LoansForBorrowerFinder;
import org.folio.circulation.domain.anonymization.service.LoansForTenantFinder;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.infrastructure.storage.loans.LoanRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.http.client.PageLimit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoanAnonymization {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public static final PageLimit FETCH_LOANS_PAGE_LIMIT = limit(5000);

  private final Clients clients;

  public LoanAnonymization(Clients clients) {
    this.clients = clients;
  }

  public LoanAnonymizationService byUserId(String userId) {
    log.info("Initializing loan anonymization for borrower");

    return createService(new AnonymizationCheckersService(),
      new LoansForBorrowerFinder(clients, userId));
  }

  public LoanAnonymizationService byCurrentTenant(LoanAnonymizationConfiguration config) {
    log.info("Initializing loan anonymization for current tenant");

    return createService(new AnonymizationCheckersService(config),
      new LoansForTenantFinder(new LoanRepository(clients), new AccountRepository(clients)));
  }

  private DefaultLoanAnonymizationService createService(
    AnonymizationCheckersService anonymizationCheckersService,
    LoanAnonymizationFinderService loansFinderService) {

    return new DefaultLoanAnonymizationService(anonymizationCheckersService, loansFinderService,
      new AnonymizeStorageLoansRepository(clients),
      new EventPublisher(clients.pubSubPublishingService()));
  }
}
