package org.folio.circulation.support.http.server;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ValidationError {
  private final String message;
  private final Map<String, String> parameters;

  public ValidationError(String message, String key, String value) {
    this.message = message;
    this.parameters = new HashMap<>();
    this.parameters.put(key, value);
  }

  public ValidationError(String message, Map<String, String> parameters) {
    this.message = message;
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
      .put("message", message)
      .put("parameters", mappedParameters);
  }

  public String getMessage() {
    return message;
  }

  public boolean hasParameter(String key) {
    return parameters.containsKey(key);
  }

  public boolean hasParameter(String key, String value) {
    return StringUtils.equals(getParameter(key), value);
  }

  public String getParameter(String key) {
    return parameters.getOrDefault(key, null);
  }

  @Override
  public String toString() {
    return String.format("reason: \"%s\", parameters: %s", message,
      parameters.keySet().stream()
        .map(key ->
          String.format("key: %s, value: %s", key, parameters.get(key)))
        .collect(Collectors.joining("%n")));
  }
}
