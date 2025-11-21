package org.folio.circulation.support;

import static org.folio.circulation.support.http.server.JsonHttpResponse.unprocessableEntity;
import static org.folio.circulation.support.results.Result.failed;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.support.http.server.ValidationError;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ValidationErrorFailure implements HttpFailure {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final Collection<ValidationError> errors = new ArrayList<>();

  public static <T> Result<T> failedValidation(String reason, String key, String value) {
    return failedValidation(new ValidationError(reason, key, value));
  }

  public static <T> Result<T> failedValidation(String message, String key, String value, ErrorCode errorCode) {
    return failedValidation(new ValidationError(message, key, value, errorCode));
  }

  public static <T> Result<T> failedValidation(String reason, Map<String, String> parameters) {
    return failedValidation(new ValidationError(reason, parameters));
  }

  public static <T> Result<T> failedValidation(String reason, Map<String, String> parameters,
    ErrorCode errorCode) {

    return failedValidation(new ValidationError(reason, parameters, errorCode));
  }

  public static <T> Result<T> failedValidation(ValidationError error) {
    return failed(singleValidationError(error));
  }

  public static <T> Result<T> failedValidation(Collection<ValidationError> errors) {
    return failed(new ValidationErrorFailure(errors));
  }

  public static ValidationErrorFailure singleValidationError(String reason,
    String propertyName, String propertyValue) {

    return singleValidationError(
      new ValidationError(reason, propertyName, propertyValue));
  }

  public static ValidationErrorFailure singleValidationError(String reason,
    String propertyName, String propertyValue, ErrorCode errorCode) {

    return singleValidationError(new ValidationError(reason, propertyName, propertyValue,
      errorCode));
  }

  public static ValidationErrorFailure singleValidationError(ValidationError error) {
    return new ValidationErrorFailure(error);
  }

  public ValidationErrorFailure(ValidationError error) {
    this.errors.add(error);
  }

  public ValidationErrorFailure(Collection<ValidationError> errors) {
    this.errors.addAll(errors);
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    final JsonObject jsonErrors = asJson();

    log.debug("Writing validation error: '{}'", jsonErrors);

    unprocessableEntity(jsonErrors).writeTo(response);
  }

  private JsonObject asJson() {
    JsonArray mappedErrors = new JsonArray(
      errors.stream()
      .map(ValidationError::toJson)
      .collect(Collectors.toList()));

    return new JsonObject().put("errors", mappedErrors);
  }

  @Override
  public String toString() {
    return String.format("validation failure:%n%s",
      errors.stream()
        .map(ValidationError::toString)
        .collect(Collectors.joining("%n")));
  }

  public boolean hasErrorWithReason(String reason) {
    return errors.stream()
      .anyMatch(error -> StringUtils.equals(error.getMessage(), reason));
  }

  public boolean hasErrorForKey(String key) {
    return errors.stream()
      .anyMatch(error -> error.hasParameter(key));
  }

  public Collection<ValidationError> getErrors() {
    return new ArrayList<>(errors);
  }
}
