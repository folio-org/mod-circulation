package org.folio.circulation.domain.anonymization;
import java.lang.invoke.MethodHandles;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersService;
import org.folio.circulation.infrastructure.storage.loans.AnonymizeStorageLoansRepository;
import org.folio.circulation.services.EventPublisher;
import org.folio.circulation.support.results.Result;
public class DefaultLoanAnonymizationService implements LoanAnonymizationService {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  private final AnonymizationCheckersService anonymizationCheckersService;
  private final EventPublisher eventPublisher;

  public DefaultLoanAnonymizationService(
    AnonymizationCheckersService anonymizationCheckersService,
    AnonymizeStorageLoansRepository anonymizeStorageLoansRepository,
    EventPublisher eventPublisher) {
    this.anonymizationCheckersService = anonymizationCheckersService;
    this.anonymizeStorageLoansRepository = anonymizeStorageLoansRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans(
    Supplier<CompletableFuture<Result<Collection<Loan>>>> loansToCheck) {
    log.debug("anonymizeLoans:: attempting to anonymize loans");
    if (anonymizationCheckersService.neverAnonymizeLoans()) {
      log.info("anonymizeLoans:: loan anonymization is disabled");
      return completedFuture(Result.of(LoanAnonymizationRecords::new));
    }

    return loansToCheck.get()
      .thenApply(r -> r.map(new LoanAnonymizationRecords()::withLoansFound))
      .thenCompose(this::segregateLoanRecords)
      .thenCompose(r -> r.after(anonymizeStorageLoansRepository::postAnonymizeStorageLoans))
      .thenCompose(r -> r.after(eventPublisher::publishAnonymizeEvents));
  }

  private CompletableFuture<Result<LoanAnonymizationRecords>> segregateLoanRecords(
    Result<LoanAnonymizationRecords> anonymizationRecords) {

    log.debug("segregateLoanRecords:: segregating loan records for anonymization");
    return completedFuture(anonymizationRecords.map(records -> {
      Map<String, Set<String>> segregatedLoans = anonymizationCheckersService
          .segregateLoans(records.getLoansFound());

      return records.withAnonymizedLoans(segregatedLoans.remove(CAN_BE_ANONYMIZED_KEY))
        .withNotAnonymizedLoans(segregatedLoans);
    }));
  }
}
