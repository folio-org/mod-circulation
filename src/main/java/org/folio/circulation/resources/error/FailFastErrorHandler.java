package org.folio.circulation.resources.error;

import static java.util.Collections.emptyMap;
import static org.folio.circulation.support.results.Result.failed;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class FailFastErrorHandler extends CirculationErrorHandler {

  public FailFastErrorHandler() {
    super(emptyMap()); // immutable empty map to disallow adding errors
  }

  @Override
  public <T> Result<T> handle(Result<T> result, CirculationError errorType, T returnValue) {
    return result;
  }

  @Override
  public <T> Result<T> handle(HttpFailure error, CirculationError errorType, T returnValue) {
    return failed(error);
  }

  @Override
  public <T> Result<T> handle(Result<T> result, CirculationError errorType, Result<T> returnValue) {
    return result;
  }

  @Override
  public <T> Result<T> handle(HttpFailure error, CirculationError errorType,
    Result<T> returnResult) {

    return failed(error);
  }
}
