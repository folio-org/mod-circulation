package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.ValidationError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import static org.folio.circulation.support.http.server.JsonResponse.response;

public class ValidationErrorFailure implements HttpFailure {
  private final Collection<ValidationError> errors = new ArrayList<>();

  public static <T> HttpResult<T> failedResult(
    String reason,
    String key,
    String value) {

    return HttpResult.failed(
      failure(new ValidationError(reason, key, value)));
  }

  public static <T> HttpResult<T> failedResult(ValidationError error) {
    return HttpResult.failed(failure(error));
  }

  public static ValidationErrorFailure failure(
    String reason,
    String propertyName,
    String propertyValue) {

    return failure(new ValidationError(reason, propertyName, propertyValue));
  }

  public static ValidationErrorFailure failure(ValidationError error) {
    return new ValidationErrorFailure(error);
  }

  public static ValidationErrorFailure failure(Collection<ValidationError> errors) {
    return new ValidationErrorFailure(errors);
  }

  public ValidationErrorFailure(ValidationError error) {
    this.errors.add(error);
  }

  public ValidationErrorFailure(Collection<ValidationError> errors) {
    this.errors.addAll(errors);
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response(response, asJson(), 422);
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
    return String.format("Validation failure:%n%s",
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
