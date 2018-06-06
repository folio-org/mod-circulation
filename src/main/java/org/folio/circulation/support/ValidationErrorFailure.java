package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.server.ValidationError;

import static org.folio.circulation.support.http.server.JsonResponse.response;

public class ValidationErrorFailure implements HttpFailure {
  private final ValidationError error;

  public static <T> HttpResult<T> failure(
    String reason,
    String key,
    String value) {

    return HttpResult.failure(error(reason, key, value));
  }

  public static ValidationErrorFailure error(
    String reason,
    String propertyName,
    String propertyValue) {

    return new ValidationErrorFailure(reason, propertyName, propertyValue);
  }

  private ValidationErrorFailure(
    String reason,
    String propertyName,
    String propertyValue) {

    this.error = new ValidationError(reason, propertyName, propertyValue);
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response(response, asJson(), 422);
  }

  private JsonObject asJson() {
    JsonArray errors = new JsonArray();

    errors.add(error.toJson());

    return new JsonObject().put("errors", errors);
  }

  @Override
  public String toString() {
    return String.format("Validation failure, reason: \"%s\", " +
        "key: \"%s\" value: \"%s\"",
      error.reason, error.key, error.value);
  }

  public boolean hasReason(String reason) {
    return StringUtils.equals(error.reason, reason);
  }

  public boolean isForKey(String key) {
    return StringUtils.equals(error.key, key);
  }
}
