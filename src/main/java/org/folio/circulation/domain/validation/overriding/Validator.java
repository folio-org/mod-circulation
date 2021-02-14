package org.folio.circulation.domain.validation.overriding;

import java.util.concurrent.CompletableFuture;

import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.support.results.Result;

public interface Validator<T> {

  CompletableFuture<Result<T>> validate(T t);
  CirculationErrorType getErrorType();
}