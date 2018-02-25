package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.JsonResponse;

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
    JsonResponse.unprocessableEntity(response,
      reason, propertyName, propertyValue);
  }
}
