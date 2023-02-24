package org.folio.circulation.resources.handlers.error;

import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.folio.circulation.support.ErrorCode;
import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ValidationErrorFailure;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class CirculationErrorHandler {
  private final Map<HttpFailure, CirculationErrorType> errors;

  public abstract <T> Result<T> handleAnyResult(Result<T> result,
    CirculationErrorType errorType, Result<T> otherwise);

  public abstract <T> Result<T> handleAnyError(HttpFailure error,
    CirculationErrorType errorType, Result<T> otherwise);

  public abstract <T> Result<T> handleValidationResult(Result<T> result,
    CirculationErrorType errorType, Result<T> otherwise);

  public abstract <T> Result<T> handleValidationError(HttpFailure error,
    CirculationErrorType errorType, Result<T> otherwise);

  public abstract <T> Result<T> failWithValidationErrors(T otherwise);

  public <T> Result<T> handleValidationResult(Result<T> result,
    CirculationErrorType errorType, T otherwise) {

    return handleValidationResult(result, errorType, succeeded(otherwise));
  }

  public <T> Result<T> handleValidationError(HttpFailure error,
    CirculationErrorType errorType, T otherwise) {

    return handleValidationError(error, errorType, succeeded(otherwise));
  }

  public boolean hasAny(CirculationErrorType... errorTypes) {
    return Arrays.stream(errorTypes)
      .anyMatch(errors::containsValue);
  }

  public boolean hasNone(CirculationErrorType... errorTypes) {
    return Arrays.stream(errorTypes)
      .noneMatch(errors::containsValue);
  }

  public boolean hasAny(ErrorCode... errorCodes) {
    var validationErrorCodes = errors.keySet().stream()
      .filter(ValidationErrorFailure.class::isInstance)
      .map(ValidationErrorFailure.class::cast)
      .map(ValidationErrorFailure::getErrors)
      .flatMap(Collection::stream)
      .map(ValidationError::getCode)
      .collect(Collectors.toSet());

    return Arrays.stream(errorCodes)
      .anyMatch(validationErrorCodes::contains);
  }
}
