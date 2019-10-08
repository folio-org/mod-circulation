package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;

import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.folio.circulation.domain.AnonymizeStorageLoansRepository;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.anonymization.checks.AnonymizationChecker;
import org.folio.circulation.support.Result;

/**
 * Checks loan eligibility for anonymization.
 * By default a loan can only be anonymized if it's closed and there are no fees
 * and fines associated with it.
 *
 */
public class DefaultLoanAnonymizationService implements LoanAnonymizationService {

  private static final String CAN_BE_ANONYMIZED_KEY = "_";

  private final LoanAnonymizationHelper anonymization;
  private final AnonymizeStorageLoansRepository anonymizeStorageLoansRepository;

  DefaultLoanAnonymizationService(LoanAnonymizationHelper anonymization) {
    this.anonymization = anonymization;
    anonymizeStorageLoansRepository = new AnonymizeStorageLoansRepository(anonymization.clients());
  }

  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans() {

    return anonymization.loansFinder().findLoansToAnonymize()
      .thenApply(r -> r.map(new LoanAnonymizationRecords()::withLoansFound))
      .thenCompose(this::segregateLoans)
      .thenCompose(r -> r.after(anonymizeStorageLoansRepository::postAnonymizeStorageLoans));
  }

  private CompletableFuture<Result<LoanAnonymizationRecords>> segregateLoans(
      Result<LoanAnonymizationRecords> anonymizationRecords) {

    return completedFuture(anonymizationRecords.map(records -> {
      HashSetValuedHashMap<String, String> multiMap = new HashSetValuedHashMap<>();
      for (Loan loan : records.getLoansFound()) {
        for (AnonymizationChecker checker : anonymization.anonymizationCheckers()) {
          if (!checker.canBeAnonymized(loan)) {
            multiMap.put(checker.getReason(), loan.getId());
          } else {
            multiMap.put(CAN_BE_ANONYMIZED_KEY, loan.getId());
          }
        }
      }

      return records.withAnonymizedLoans(multiMap.remove(CAN_BE_ANONYMIZED_KEY))
        .withNotAnonymizedLoans(multiMap);
    }));

  }
}
