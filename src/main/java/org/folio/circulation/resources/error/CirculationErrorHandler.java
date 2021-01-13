package org.folio.circulation.resources.error;

import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Arrays;
import java.util.Map;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.MultiErrorFailure;
import org.folio.circulation.support.results.Result;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class CirculationErrorHandler {
  private final Map<HttpFailure, CirculationError> errors;

  public abstract <T> Result<T> handleResult(Result<T> result, CirculationError errorType,
    Result<T> returnValue);

  public abstract <T> Result<T> handleError(HttpFailure error, CirculationError errorType,
    Result<T> returnValue);

  public abstract <T> Result<T> handleValidationResult(Result<T> result, CirculationError errorType,
    Result<T> returnValue);

  public abstract <T> Result<T> handleValidationError(HttpFailure error,CirculationError errorType,
    Result<T> returnValue);

  public <T> Result<T> failIfHasErrors(T returnValue) {
    return errors.isEmpty()
      ? succeeded(returnValue)
      : failed(new MultiErrorFailure(errors.keySet()));
  }

  public boolean hasAny(CirculationError... errorTypes) {
    return Arrays.stream(errorTypes)
      .anyMatch(errors::containsValue);
  }

}
