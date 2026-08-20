package org.folio.circulation.domain.anonymization.service;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.infrastructure.storage.feesandfines.AccountRepository;
import org.folio.circulation.infrastructure.storage.loans.AnonymizationDueDateStorageRepository;
import org.folio.circulation.support.results.Result;

/**
 * Drain finder for the scheduled job: fetches the closed loans whose due-date
 * has arrived, then attaches their fee/fine accounts for the re-check.
 */
public class LoansForTenantFinder extends DefaultLoansFinder {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private final AnonymizationDueDateStorageRepository dueDateStorageRepository;
  private final int numberOfLoansToCheck;

  public LoansForTenantFinder(AnonymizationDueDateStorageRepository dueDateStorageRepository,
    AccountRepository accountRepository, int numberOfLoansToCheck) {

    super(accountRepository);
    this.dueDateStorageRepository = dueDateStorageRepository;
    this.numberOfLoansToCheck = numberOfLoansToCheck;
  }

  public CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize() {
    log.info("findLoansToAnonymize:: searching for up to {} due loans", numberOfLoansToCheck);
    return dueDateStorageRepository.findDue(numberOfLoansToCheck)
      .thenCompose(this::fetchAdditionalLoanInfo);
  }
}
