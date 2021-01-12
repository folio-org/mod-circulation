package org.folio.circulation.domain.validation.overriding;

import static org.folio.circulation.support.results.Result.ofAsync;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.LoanAndRelatedRecords;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public abstract class OverrideValidator implements OverrideValidation {
  private final Function<String, ValidationErrorFailure> overridingErrorFunction;
  protected static final String OKAPI_PERMISSIONS = "x-okapi-permissions";

  public OverrideValidator(Function<String, ValidationErrorFailure> overridingErrorFunction) {
    this.overridingErrorFunction = overridingErrorFunction;
  }

  @Override
  public CompletableFuture<Result<LoanAndRelatedRecords>> validate(LoanAndRelatedRecords records) {
    return ofAsync(() -> records)
      .thenCompose(result -> result.failAfter(relatedRecords -> isOverridingItemLimitAllowed(),
        relatedRecords -> overridingErrorFunction.apply("Missing override permissions")));
  }

  protected abstract CompletableFuture<Result<Boolean>> isOverridingItemLimitAllowed();
}
