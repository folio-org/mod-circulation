package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.anonymization.config.ClosingType;
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

  private final LoanRepository loanRepository;
  private final AccountRepository accountRepository;
  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  private final EventPublisher eventPublisher;

  public LoanAnonymization(Clients clients, LoanRepository loanRepository,
    AccountRepository accountRepository) {

    this.loanRepository = loanRepository;
    this.accountRepository = accountRepository;
    anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
    eventPublisher = new EventPublisher(clients.pubSubPublishingService());
  }

  public LoanAnonymizationService byUserId(String userId) {
    log.info("Initializing loan anonymization for borrower");

    return createService(new AnonymizationCheckersService(),
      new LoansForBorrowerFinder(userId, loanRepository, accountRepository));
  }

  public LoanAnonymizationService byCurrentTenant(LoanAnonymizationConfiguration config) {
    log.info("Initializing loan anonymization for current tenant");

    if (neverAnonymizeLoans(config)) {
      return new NeverLoanAnonymizationService();
    }

    return createService(new AnonymizationCheckersService(config),
      new LoansForTenantFinder(loanRepository, accountRepository));
  }

  private boolean neverAnonymizeLoans(LoanAnonymizationConfiguration config) {
    return config.getLoanClosingType() == ClosingType.NEVER &&
      !config.treatLoansWithFeesAndFinesDifferently();
  }

  private DefaultLoanAnonymizationService createService(
    AnonymizationCheckersService anonymizationCheckersService,
    LoanAnonymizationFinderService loansFinderService) {

    return new DefaultLoanAnonymizationService(anonymizationCheckersService,
      loansFinderService, anonymizeStorageLoansRepository, eventPublisher);
  }
}
