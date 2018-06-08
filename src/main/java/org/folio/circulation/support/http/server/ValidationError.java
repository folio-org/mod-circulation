package org.folio.circulation.support.http.server;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ValidationError {
  private final String reason;
  private final Map<String, String> parameters;

  public ValidationError(String reason, String key, String value) {
    this.reason = reason;
    this.parameters = new HashMap<>();
    this.parameters.put(key, value);
  }

  public ValidationError(String reason, Map<String, String> parameters) {
    this.reason = reason;
    this.parameters = parameters;
  }

  public JsonObject toJson() {
    JsonArray mappedParameters = new JsonArray(
      parameters.keySet().stream()
        .map(key ->
          new JsonObject()
            .put("key", key)
            .put("value", parameters.get(key)))
        .collect(Collectors.toList()));

    return new JsonObject()
      .put("message", reason)
      .put("parameters", mappedParameters);
  }

  @Override
  public String toString() {
    return String.format("reason: \"%s\", parameters: %s", reason,
      parameters.keySet().stream()
        .map(key ->
          String.format("key: %s, value: %s", key, parameters.get(key)))
        .collect(Collectors.joining("%n")));
  }

  public String getReason() {
    return reason;
  }

  public boolean hasParameterWithKey(String key) {
    return parameters.containsKey(key);
  }
}
