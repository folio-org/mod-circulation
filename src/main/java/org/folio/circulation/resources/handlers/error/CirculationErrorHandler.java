package org.folio.circulation.resources.handlers.error;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
    CirculationErrorType errorType, T otherwise);

  public abstract <T> Result<T> handleValidationResult(Result<T> result,
    CirculationErrorType errorType, Result<T> otherwise);

  public abstract <T> Result<T> handleValidationError(HttpFailure error,
    CirculationErrorType errorType, T otherwise);

  public abstract <T> Result<T> handleValidationError(HttpFailure error,
    CirculationErrorType errorType, Result<T> otherwise);

  public <T> Result<T> failWithValidationErrors(T otherwise) {
    List<ValidationError> validationErrors = errors.keySet().stream()
      .filter(ValidationErrorFailure.class::isInstance)
      .map(ValidationErrorFailure.class::cast)
      .map(ValidationErrorFailure::getErrors)
      .flatMap(Collection::stream)
      .collect(toList());

    return validationErrors.isEmpty()
      ? succeeded(otherwise)
      : failed(new ValidationErrorFailure(validationErrors));
  }

  public boolean hasAny(CirculationErrorType... errorTypes) {
    return Arrays.stream(errorTypes)
      .anyMatch(errors::containsValue);
  }

  public boolean hasNone(CirculationErrorType... errorTypes) {
    return Arrays.stream(errorTypes)
      .noneMatch(errors::containsValue);
  }
}