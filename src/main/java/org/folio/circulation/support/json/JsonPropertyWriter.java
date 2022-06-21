package org.folio.circulation.support.json;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonPropertyWriter {
  private JsonPropertyWriter() { }

  public static void write(
    JsonObject to,
    String propertyName,
    String value) {

    if(StringUtils.isNotBlank(value)) {
      to.put(propertyName, value);
    }
  }

  public static void write(
    JsonObject to,
    String propertyName,
    Double value) {
      to.put(propertyName, value);
    }

  public static void write(
    JsonObject to,
    String propertyName,
    JsonArray value) {

    if(value != null && !value.isEmpty()) {
      to.put(propertyName, value);
    }
  }

  public static <T> void write(JsonObject to, String propertyName, Collection<T> value) {
    if (value != null && !value.isEmpty()) {
      to.put(propertyName, value);
    }
  }

  public static void write(
    JsonObject to,
    String propertyName,
    JsonObject value) {

    if(value != null) {
      to.put(propertyName, value);
    }
  }

  public static void write(
    JsonObject to,
    String propertyName,
    Integer value) {

    if(value != null) {
      to.put(propertyName, value);
    }
  }

  public static void write(
    JsonObject to,
    String propertyName,
    Boolean value) {

    if(value != null) {
      to.put(propertyName, value);
    }
  }

  public static void write(JsonObject to, String propertyName, ZonedDateTime value) {
    if (value != null) {
      write(to, propertyName, formatDateTime(value.withZoneSameInstant(ZoneOffset.UTC)));
    }
  }

  public static void write(JsonObject to, String propertyName, UUID value) {
    if(value != null) {
      write(to, propertyName, value.toString());
    }
  }

  public static void writeNamedObject(
    JsonObject to,
    String propertyName,
    String value) {

    if(StringUtils.isNotBlank(value)) {
      to.put(propertyName, new JsonObject().put("name", value));
    }
  }

  public static void remove(
    JsonObject from,
    String propertyName) {

    if(from == null) {
      return;
    }

    from.remove(propertyName);
  }

  public static void writeByPath(JsonObject to, String value, String... paths) {
    writeByPath(to, JsonPropertyWriter::write, value, paths);
  }

  public static void writeByPath(JsonObject to, ZonedDateTime value, String... paths) {
    writeByPath(to, JsonPropertyWriter::write, value, paths);
  }

  public static void writeByPath(JsonObject to, boolean value, String... paths) {
    writeByPath(to, JsonPropertyWriter::write, value, paths);
  }

  private static <T> void writeByPath(JsonObject to, JsonPropertySetter<T> setter,
    T value, String... paths) {

    if (to == null || value == null || paths.length == 0) {
      return;
    }

    JsonObject currentObject = to;
    for (int pathIndex = 0; pathIndex < paths.length - 1; pathIndex++) {
      final String currentPath = paths[pathIndex];

      if (!currentObject.containsKey(currentPath)) {
        currentObject.put(currentPath, new JsonObject());
      }

      currentObject = currentObject.getJsonObject(currentPath);
    }

    setter.set(currentObject, paths[paths.length - 1], value);
  }

  public static JsonObject copyProperty(JsonObject source, JsonObject destination,
    String propertyName) {

    return Optional.ofNullable(source)
      .map(json -> json.getValue(propertyName))
      .map(value -> destination.put(propertyName, value))
      .orElse(destination);
  }

  @FunctionalInterface
  private interface JsonPropertySetter<T> {
    void set(JsonObject object, String path, T value);
  }
}
