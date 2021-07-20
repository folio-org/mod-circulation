package org.folio.circulation.domain.anonymization;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;

public interface LoanAnonymizationService {
  CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans(
    Supplier<CompletableFuture<Result<Collection<Loan>>>> loansToCheck);
}
