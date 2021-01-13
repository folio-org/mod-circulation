package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.failed;

import java.util.LinkedHashMap;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class DeferFailureErrorHandler extends CirculationErrorHandler {

  public DeferFailureErrorHandler() {
    super(new LinkedHashMap<>());
  }

  @Override
  public <T> Result<T> handleResult(Result<T> result, CirculationError errorType,
    Result<T> returnValue) {

    return result.mapFailure(error -> handleError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleError(HttpFailure error, CirculationError errorType,
    Result<T> returnValue) {

    getErrors().put(error, errorType);
    return returnValue;
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationError errorType,
    Result<T> returnValue) {

    return result.mapFailure(error -> handleValidationError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationError errorType,
    Result<T> returnValue) {

    return error instanceof ValidationErrorFailure
      ? handleError(error, errorType, returnValue)
      : failed(error);
  }

}
