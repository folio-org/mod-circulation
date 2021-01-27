package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.LinkedHashMap;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.results.Result;

public class DeferFailureErrorHandler extends CirculationErrorHandler {

  public DeferFailureErrorHandler() {
    super(new LinkedHashMap<>());
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationError errorType, T returnValue) {
    return result.mapFailure(error -> handleValidationError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationError errorType, T returnValue) {
    getErrors().put(error, errorType);

    if (error instanceof ValidationErrorFailure) {
      return succeeded(returnValue);
    }

    return failed(error);
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationError errorType, Result<T> returnValue) {
    return result.mapFailure(error -> handleValidationError(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationError errorType,
    Result<T> returnResult) {

    getErrors().put(error, errorType);

    if (error instanceof ValidationErrorFailure) {
      return returnResult;
    }

    return failed(error);
  }

}
