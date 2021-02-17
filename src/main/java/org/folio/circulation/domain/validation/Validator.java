package org.folio.circulation.domain.validation;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.support.results.Result;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Validator<T> {
  private final CirculationErrorType errorType;
  private final Function<T, CompletableFuture<Result<T>>> validationFunction;

  public CompletableFuture<Result<T>> validate(T objectToValidate) {
    return validationFunction.apply(objectToValidate);
  }

  public CirculationErrorType getErrorType() {
    return errorType;
  }
}
