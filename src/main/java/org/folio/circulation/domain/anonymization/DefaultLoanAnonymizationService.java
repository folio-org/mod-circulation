package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.domain.anonymization.service.LoanAnonymizationFinderService;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;

public class DefaultLoanAnonymizationService implements LoanAnonymizationService {
  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  private final AnonymizationCheckersService anonymizationCheckersService;
  private final LoanAnonymizationFinderService loansFinder;
  private final EventPublisher eventPublisher;

  DefaultLoanAnonymizationService(AnonymizationCheckersService anonymizationCheckersService,
    LoanAnonymizationFinderService loansFinderService,
    AnonymizeStorageLoansRepository anonymizeStorageLoansRepository, EventPublisher eventPublisher) {

    this.anonymizationCheckersService = anonymizationCheckersService;
    this.loansFinder = loansFinderService;
    this.anonymizeStorageLoansRepository = anonymizeStorageLoansRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans() {
    return loansFinder.findLoansToAnonymize()
      .thenApply(r -> r.map(new LoanAnonymizationRecords()::withLoansFound))
      .thenCompose(this::segregateLoanRecords)
      .thenCompose(r -> r.after(anonymizeStorageLoansRepository::postAnonymizeStorageLoans))
      .thenCompose(r -> r.after(eventPublisher::publishAnonymizeEvents));
  }

  private CompletableFuture<Result<LoanAnonymizationRecords>> segregateLoanRecords(
      Result<LoanAnonymizationRecords> anonymizationRecords) {

    return completedFuture(anonymizationRecords.map(records -> {
      Map<String, Set<String>> segregatedLoans = anonymizationCheckersService
          .segregateLoans(records.getLoansFound());

      return records.withAnonymizedLoans(segregatedLoans.remove(CAN_BE_ANONYMIZED_KEY))
        .withNotAnonymizedLoans(segregatedLoans);
    }));
  }
}
