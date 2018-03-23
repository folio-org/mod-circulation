package org.folio.circulation.support.http.server;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ValidationError {

  public final String message;
  public final String propertyName;
  public final String value;

  public ValidationError(String message, String propertyName, String value) {
    this.message = message;
    this.propertyName = propertyName;
    this.value = value;
  }

  public JsonObject toJson() {
    JsonArray parameters = new JsonArray();

    parameters.add(new JsonObject()
      .put("key", propertyName)
      .put("value", value));

    return new JsonObject()
      .put("message", message)
      .put("parameters", parameters);
  }
}
