package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeferFailureErrorHandler extends CirculationErrorHandler {
  final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public DeferFailureErrorHandler() {
    super(new LinkedHashMap<>());
  }

  @Override
  public <T> Result<T> handleAnyResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result.mapFailure(error -> handleAnyError(error, errorType, otherwise));
  }

  @Override
  public <T> Result<T> handleAnyError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    if (error != null) {
      getErrors().put(error, errorType);
    } else {
      log.warn("HttpFailure object for error of type {} is null, error is ignored", errorType);
    }

    return otherwise;
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationErrorType errorType,
    T otherwise) {

    return handleValidationResult(result, errorType, succeeded(otherwise));
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result.mapFailure(error -> handleValidationError(error, errorType, otherwise));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    T otherwise) {

    return handleValidationError(error, errorType, succeeded(otherwise));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    return error instanceof ValidationErrorFailure
      ? handleAnyError(error, errorType, otherwise)
      : failed(error);
  }
}