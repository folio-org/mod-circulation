package org.folio.circulation.support.http.server;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class ValidationError {
  private final String code;
  private final String message;
  private final Map<String, String> parameters;

  /**
   * Internationalized error.
   *
   * @param code translation key
   * @param message fall-back message to display if there is no message for code,
   *            any {key} is replaced by value
   */
  public ValidationError(String code, String message, String key, String value) {
    this.code = code;
    this.message = message == null ? null : message.replace('{' + key + '}', value);
    this.parameters = Map.of(key, value);
  }

  /**
   * Internationalized error.
   *
   * @param code translation key
   * @param message fall-back message to display if there is no message for code,
   *            any {key1} is replaced by value1, any {key2} is replaced by {value2},
   *            any {key3} is replaced by {value3}
   */
  public ValidationError(String code, String message,
      String key1, String value1, String key2, String value2, String key3, String value3) {

    this.code = code;
    this.parameters = Map.of(key1, value1, key2, value2, key3, value3);
    this.message = StringSubstitutor.replace(message, parameters, "{", "}");
  }

  /**
   * Error message without translation code and without placeholder replacement.
   */
  public ValidationError(String message, String key, String value) {
    this.code = null;
    this.message = message;
    this.parameters = new HashMap<>();
    this.parameters.put(key, value);
  }

  /**
   * Error message without translation code and without placeholder replacement.
   */
  public ValidationError(String message, Map<String, String> parameters) {
    this.code = null;
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

    JsonObject jsonObject = new JsonObject();
    if (code != null) {
      jsonObject.put("code", code);
    }
    if (message != null) {
      jsonObject.put("message", message);
    }
    return jsonObject.put("parameters", mappedParameters);
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
    return (code == null ? "" : "code: " + code + ", ")
        + (message == null ? "" : "reason: " + message + ", ")
        + "parameters: " + parameters.entrySet().stream()
        .map(entry -> "key: " + entry.getKey() + ", value: " + entry.getValue())
        .collect(Collectors.joining("\n"));
  }
}
