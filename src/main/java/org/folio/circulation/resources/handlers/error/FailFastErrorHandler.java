package org.folio.circulation.resources.handlers.error;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.results.Result.failed;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class FailFastErrorHandler extends CirculationErrorHandler {

  public FailFastErrorHandler() {
    super(emptyMap()); // immutable empty map to disallow adding errors
  }

  @Override
  public <T> Result<T> handleResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result;
  }

  @Override
  public <T> Result<T> handleError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    return failed(error);
  }

  @Override
  public <T> Result<T> handleValidationResult(Result<T> result, CirculationErrorType errorType,
    Result<T> otherwise) {

    return result;
  }

  @Override
  public <T> Result<T> handleValidationError(HttpFailure error, CirculationErrorType errorType,
    Result<T> otherwise) {

    return failed(error);
  }

}
