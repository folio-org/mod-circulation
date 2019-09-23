package org.folio.circulation.domain.anonymization;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.Result;

public interface LoanAnonymizationFinderService {
  CompletableFuture<Result<Collection<Loan>>> findLoansToAnonymize();
}
