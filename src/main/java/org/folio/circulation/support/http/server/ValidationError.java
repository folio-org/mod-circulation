package org.folio.circulation.support.http.server;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.folio.circulation.support.json.JsonPropertyWriter;

@Getter
@EqualsAndHashCode
public class ValidationError {
  private final String message;
  private final Map<String, String> parameters;
  private final String code;

  public ValidationError(String message, String key, String value) {
    this.message = message;
    this.parameters = new HashMap<>();
    this.parameters.put(key, value);
    this.code = null;
  }

  public ValidationError(String message, String key, String value, String code) {
    this.message = message;
    this.parameters = new HashMap<>();
    this.parameters.put(key, value);
    this.code = code;
  }

  public ValidationError(String message, Map<String, String> parameters) {
    this.message = message;
    this.parameters = parameters;
    this.code = null;
  }

  public ValidationError(String message, Map<String, String> parameters, String code) {
    this.message = message;
    this.parameters = parameters;
    this.code = code;
  }

  public JsonObject toJson() {
    JsonArray mappedParameters = new JsonArray(
      parameters.keySet().stream()
        .map(key ->
          new JsonObject()
            .put("key", key)
            .put("value", parameters.get(key)))
        .collect(Collectors.toList()));

    JsonObject result = new JsonObject()
      .put("message", message)
      .put("parameters", mappedParameters);
    JsonPropertyWriter.write(result, "code", code);

    return result;
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
