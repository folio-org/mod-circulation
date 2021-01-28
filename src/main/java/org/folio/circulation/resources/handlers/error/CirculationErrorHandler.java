package org.folio.circulation.resources.handlers.error;

import static java.util.stream.Collectors.toList;
import static org.folio.circulation.support.results.Result.failed;
import static org.folio.circulation.support.results.Result.succeeded;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.folio.circulation.support.HttpFailure;
import org.folio.circulation.support.ServerErrorFailure;
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

  public <T> Result<T> handleValidationResult(Result<T> result,
    CirculationErrorType errorType, T otherwise) {

    return handleValidationResult(result, errorType, succeeded(otherwise));
  }

  public <T> Result<T> handleValidationError(HttpFailure error,
    CirculationErrorType errorType, T otherwise) {

    return handleValidationError(error, errorType, succeeded(otherwise));
  }

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

  public <T> Result<T> failIfErrorsExist(Result<T> result) {
    if (result.succeeded() && errors.isEmpty()) {
      return result;
    }

    boolean onlyValidationErrorsFound = errors.keySet().stream()
      .allMatch(ValidationErrorFailure.class::isInstance);
    boolean overridableErrorsFound = errors.values().stream()
      .anyMatch(CirculationErrorType::isOverridable);

    if (errors.size() == 1 || !overridableErrorsFound) {
      // If there's only one error or if there are no overridable errors - fail with the first error
      var failure = errors.keySet().stream().findFirst()
        .orElse(new ServerErrorFailure("Unknown error"));

      return failed(failure);
    }
    else {
      // If there are overridable errors - return full list of errors

      if (onlyValidationErrorsFound) {
        // Join validation failures (non-overridable only)
        ValidationErrorFailure joinedValidationErrorFailure = new ValidationErrorFailure(
          errors.keySet().stream()
            .filter(e -> !errors.get(e).isOverridable())
            .map(ValidationErrorFailure.class::cast)
            .map(ValidationErrorFailure::getErrors)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));

        // Create a separate error for overridable errors
        Map<String, String> parameters = new HashMap<>();
        parameters.put("overridableBlocks", errors.values().stream()
          .map(CirculationErrorType::getOverridableBlock)
          .filter(Objects::nonNull)
          .map(OverridableBlock::getBlockName)
          .filter(Objects::nonNull)
          .collect(Collectors.joining(",")));
        parameters.put("missingOverridePermissions", "");
        new ValidationError("Overridable blocks found", parameters);

        return failed(joinedValidationErrorFailure);
      }
      else {
        // Failing with the first non-validation error
        return failed(new ServerErrorFailure("Unknown error"));
      }
    }
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