package org.folio.circulation.domain.anonymization;

import static org.folio.circulation.support.http.client.PageLimit.limit;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.anonymization.config.ClosingType;
import org.folio.circulation.domain.anonymization.config.LoanAnonymizationConfiguration;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.http.client.PageLimit;

public class LoanAnonymization {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public static final PageLimit FETCH_LOANS_PAGE_LIMIT = limit(5000);

  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  private final EventPublisher eventPublisher;

  public LoanAnonymization(AnonymizeStorageLoansRepository anonymizeStorageLoansRepository,
    EventPublisher eventPublisher) {

    this.anonymizeStorageLoansRepository = anonymizeStorageLoansRepository;
    this.eventPublisher = eventPublisher;
  }

  public LoanAnonymizationService byUserId() {
    log.info("Initializing loan anonymization for borrower");

    return createService(new AnonymizationCheckersService());
  }

  public LoanAnonymizationService byCurrentTenant(
          LoanAnonymizationConfiguration config) {
    log.info("Initializing loan anonymization for current tenant");

    if (neverAnonymizeLoans(config)) {
      return new NeverLoanAnonymizationService();
    }

    return createService(new AnonymizationCheckersService(config));
  }

  private boolean neverAnonymizeLoans(LoanAnonymizationConfiguration config) {
    return config.getLoanClosingType() == ClosingType.NEVER &&
      !config.treatLoansWithFeesAndFinesDifferently();
  }

  private DefaultLoanAnonymizationService createService(
          AnonymizationCheckersService anonymizationCheckersService) {

    return new DefaultLoanAnonymizationService(anonymizationCheckersService,
            anonymizeStorageLoansRepository, eventPublisher);
  }
}
