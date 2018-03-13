package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;

import java.util.UUID;

public class JsonBuilder {
  protected void put(JsonObject request, String property, String value) {
    if(value != null) {
      request.put(property, value);
    }
  }

  protected void put(JsonObject request, String property, UUID value) {
    if(value != null) {
      request.put(property, value.toString());
    }
  }

  protected void put(JsonObject request, String property, DateTime value) {
    if(value != null) {
      request.put(property, value.toString(ISODateTimeFormat.dateTime()));
    }
  }

  protected void put(
    JsonObject request,
    String property,
    Object check,
    JsonObject value) {

    if(check != null) {
      request.put(property, value);
    }
  }
}
