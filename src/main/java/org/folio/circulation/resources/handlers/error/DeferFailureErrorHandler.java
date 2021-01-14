package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.support.results.Result.failed;

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
  public <T> Result<T> handleResult(Result<T> result, CirculationErrorType errorType,
    Result<T> returnValue) {

    return result.mapFailure(error -> handleError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleError(HttpFailure error, CirculationErrorType errorType,
    Result<T> returnValue) {

    if (error != null) {
      getErrors().put(error, errorType);
    } else {
      log.warn("HttpFailure object for error of type {} is null, error is ignored", errorType);
    }

    return returnValue;
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationErrorType errorType,
    Result<T> returnValue) {

    return result.mapFailure(error -> handleValidationError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    Result<T> returnValue) {

    return error instanceof ValidationErrorFailure
      ? handleError(error, errorType, returnValue)
      : failed(error);
  }

}
