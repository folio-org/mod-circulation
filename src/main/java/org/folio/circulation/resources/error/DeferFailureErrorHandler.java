package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.HashMap;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.results.Result;

public class DeferFailureErrorHandler extends CirculationErrorHandler {

  public DeferFailureErrorHandler() {
    super(new HashMap<>());
  }

  @Override
  public <T> Result<T> handle(Result<T> result, CirculationError errorType, T returnValue) {
    return result.mapFailure(error -> handle(error, errorType, returnValue));
  }

  @Override
  public <T> Result<T> handle(HttpFailure error, CirculationError errorType, T returnValue) {
    getErrors().put(error, errorType);
    return succeeded(returnValue);
  }

}
