package org.folio.circulation.domain.validation.overriding;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.results.Result;

public interface LoanValidator {

  CompletableFuture<Result<LoanAndRelatedRecords>> validate(
    LoanAndRelatedRecords records);
}
