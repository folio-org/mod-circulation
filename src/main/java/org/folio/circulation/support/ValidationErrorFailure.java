package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.ValidationError;

import static org.folio.circulation.support.http.server.JsonResponse.response;

public class ValidationErrorFailure implements HttpFailure {
  private final String reason;
  private final String propertyName;
  private final String propertyValue;

  public ValidationErrorFailure(
    String reason,
    String propertyName,
    String propertyValue) {

    this.reason = reason;
    this.propertyName = propertyName;
    this.propertyValue = propertyValue;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response(response, asJson(), 422);
  }

  private JsonObject asJson() {
    ValidationError error = new ValidationError(reason, propertyName, propertyValue);

    JsonArray errors = new JsonArray();

    errors.add(error.toJson());

    return new JsonObject().put("errors", errors);
  }

  @Override
  public String toString() {
    return String.format("Validation failure, reason: \"%s\", " +
        "property name: \"%s\" value: \"%s\"",
      reason, propertyName, propertyValue);
  }

  public String getReason() {
    return reason;
  }

  public String getPropertyName() {
    return propertyName;
  }

  public String getPropertyValue() {
    return propertyValue;
  }
}
