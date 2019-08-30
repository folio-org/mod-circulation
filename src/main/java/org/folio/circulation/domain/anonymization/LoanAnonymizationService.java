package org.folio.circulation.domain.anonymization;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.support.Result;

public interface LoanAnonymizationService {

  CompletableFuture<Result<LoanAnonymizationRecords>> anonymizeLoans(LoanAnonymizationRecords records);
}
