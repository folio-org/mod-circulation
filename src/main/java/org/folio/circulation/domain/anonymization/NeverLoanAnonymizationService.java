package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.results.Result;

public class NeverLoanAnonymizationService implements LoanAnonymizationService {
  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans(
          Supplier<CompletableFuture<Result<Collection<Loan>>>> loansToCheck) {
    return completedFuture(Result.of(LoanAnonymizationRecords::new));
  }
}
