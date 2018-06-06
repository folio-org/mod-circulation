package org.folio.circulation.support.http.server;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ValidationError {

  public final String reason;
  public final String key;
  public final String value;

  public ValidationError(String reason, String key, String value) {
    this.reason = reason;
    this.key = key;
    this.value = value;
  }

  public JsonObject toJson() {
    JsonArray parameters = new JsonArray();

    parameters.add(new JsonObject()
      .put("key", key)
      .put("value", value));

    return new JsonObject()
      .put("message", reason)
      .put("parameters", parameters);
  }

  @Override
  public String toString() {
    return String.format("reason: \"%s\", " +
        "key: \"%s\" value: \"%s\"", reason, key, value);
  }
}
