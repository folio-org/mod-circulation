package org.folio.circulation.domain.anonymization;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.results.Result;

public class NeverLoanAnonymizationService implements LoanAnonymizationService {
  @Override
  public CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans() {
    return completedFuture(Result.of(LoanAnonymizationRecords::new));
  }
}
