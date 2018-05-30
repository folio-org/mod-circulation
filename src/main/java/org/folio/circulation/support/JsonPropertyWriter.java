package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

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
    JsonArray value) {

    if(value !=null && !value.isEmpty()) {
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

  public static void writeNamedObject(
    JsonObject to,
    String propertyName,
    String value) {

    if(StringUtils.isNotBlank(value)) {
      to.put(propertyName, new JsonObject().put("name", value));
    }
  }
}
