package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Map;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.results.Result;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class CirculationErrorHandler {

  private final Map<HttpFailure, CirculationError> errors;

  public abstract <T> Result<T> handle(Result<T> result, CirculationError errorType, Result<T> returnValue);

  public abstract <T> Result<T> handle(HttpFailure error, CirculationError errorType, Result<T> returnValue);

  public <T> Result<T> failIfErrorsExist(T returnValue) {
    return getErrors().isEmpty()
      ? succeeded(returnValue)
      : failed(new ServerErrorFailure(getErrors().size() + " error(s) encountered"));
  }

}
