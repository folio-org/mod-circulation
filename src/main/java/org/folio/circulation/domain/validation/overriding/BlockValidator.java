package org.folio.circulation.domain.validation.overriding;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.folio.circulation.domain.validation.Validator;
import org.folio.circulation.resources.handlers.error.CirculationErrorType;
import org.folio.circulation.support.results.Result;

import lombok.Getter;

@Getter
public class BlockValidator<T> extends Validator<T> {
  private final CirculationErrorType errorType;

  public BlockValidator(CirculationErrorType errorType,
    Function<T, CompletableFuture<Result<T>>> validationFunction) {

    super(validationFunction);
    this.errorType = errorType;
  }
}