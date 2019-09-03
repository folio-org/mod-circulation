package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.AnonymizeStorageLoansRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.LoanRepository;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.Clients;
import org.folio.circulation.support.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class DefaultLoanAnonymizationService implements LoanAnonymizationService {

  protected final Logger log = LoggerFactory.getLogger(MethodHandles.lookup()
    .lookupClass());
  private final LoanRepository loanRepository;
  private AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;

  DefaultLoanAnonymizationService(Clients clients) {
    loanRepository = new LoanRepository(clients);
    anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(clients);
  }

  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans(LoanAnonymizationRecords records) {

    log.info("Anonymizing loans for userId: {} in tenant {}", records.getUserId(), records.getTenant());
    
    return findLoansToAnonymize(records)
        .thenCompose(this::populateLoanInformation)
          .thenApply(r -> r.map(records::withInputLoans))
        .thenCompose(this::filterNotEligibleLoans)
        .thenCompose(r -> r.after(anonymizeStorageLoansRepository::postAnonymizeStorageLoans));
  }

  protected CompletableFuture<Result<Collection<Loan>>> populateLoanInformation(Result<MultipleRecords<Loan>> records) {
    return completedFuture(records.map(MultipleRecords::getRecords));
  }

  protected CompletableFuture<Result<LoanAnonymizationRecords>> filterNotEligibleLoans(Result<LoanAnonymizationRecords> records) {
    return completedFuture(records);
  }

  private CompletableFuture<Result<MultipleRecords<Loan>>> findLoansToAnonymize(LoanAnonymizationRecords records) {
    return loanRepository.findClosedLoansForUser(records.getUserId());
  }

}
