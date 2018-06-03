package org.folio.circulation.support;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

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

  public static void write(JsonObject to, String propertyName, DateTime value) {
    if(value != null) {
      write(to, propertyName, value.toString(ISODateTimeFormat.dateTime()));
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
