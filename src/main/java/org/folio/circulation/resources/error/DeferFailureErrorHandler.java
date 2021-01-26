package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.LinkedHashMap;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

public class DeferFailureErrorHandler extends CirculationErrorHandler {

  public DeferFailureErrorHandler() {
    super(new LinkedHashMap<>());
  }

  @Override
  public <T> Result<T> handle(Result<T> result, CirculationError errorType, T returnValue) {
    return result.mapFailure(error -> handle(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handle(HttpFailure error, CirculationError errorType, T returnValue) {
    getErrors().put(error, errorType);

    if (error instanceof ValidationError) {
      return succeeded(returnValue);
    }

    return failed(error);
  }

  @Override
  public <T> Result<T> handle(Result<T> result, CirculationError errorType, Result<T> returnValue) {
    return result.mapFailure(error -> handle(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handle(HttpFailure error, CirculationError errorType,
    Result<T> returnResult) {

    getErrors().put(error, errorType);

    if (error instanceof ValidationError) {
      return returnResult;
    }

    return failed(error);
  }

}
