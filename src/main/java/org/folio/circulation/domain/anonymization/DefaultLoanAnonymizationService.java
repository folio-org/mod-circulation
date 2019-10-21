package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.folio.circulation.domain.anonymization.LoanAnonymizationRecords.CAN_BE_ANONYMIZED_KEY;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.folio.circulation.domain.AnonymizeStorageLoansRepository;
import org.folio.circulation.domain.anonymization.service.AnonymizationCheckersFacade;
import org.folio.circulation.domain.anonymization.service.LoanAnonymizationFinderService;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;

public class DefaultLoanAnonymizationService implements LoanAnonymizationService {

  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;
  private final AnonymizationCheckersFacade anonymizationCheckersFacade;
  private final LoanAnonymizationFinderService loansFinder;

  DefaultLoanAnonymizationService(Clients clients, AnonymizationCheckersFacade anonymizationCheckersFacade,
      LoanAnonymizationFinderService loansFinderService) {
    this.anonymizationCheckersFacade = anonymizationCheckersFacade;
    this.loansFinder = loansFinderService;
    anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
  }

  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans() {

    return loansFinder.findLoansToAnonymize()
      .thenApply(r -> r.map(new LoanAnonymizationRecords()::withLoansFound))
      .thenCompose(this::segregateLoanRecords)
      .thenCompose(r -> r.after(anonymizeStorageLoansRepository::postAnonymizeStorageLoans));
  }

  private CompletableFuture<Result<LoanAnonymizationRecords>> segregateLoanRecords(
      Result<LoanAnonymizationRecords> anonymizationRecords) {

    return completedFuture(anonymizationRecords.map(records -> {
      HashSetValuedHashMap<String, String> segregatedLoans = anonymizationCheckersFacade.segregateLoans(records.getLoansFound());
      return records.withAnonymizedLoans(segregatedLoans.remove(CAN_BE_ANONYMIZED_KEY))
        .withNotAnonymizedLoans(segregatedLoans);
    }));

  }
}
