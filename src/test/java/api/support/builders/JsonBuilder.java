package api.support.builders;

import java.util.UUID;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonBuilder {
  protected void put(JsonObject representation, String property, String value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, Integer value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, Double value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, UUID value) {
    if(value != null) {
      representation.put(property, value.toString());
    }
  }

  protected void put(JsonObject representation, String property, Boolean value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, DateTime value) {
    if(value != null) {
      representation.put(property, value.toString(ISODateTimeFormat.dateTime()));
    }
  }

  protected void put(JsonObject representation, String propertyName, LocalDate value) {
    if(value != null) {
      representation.put(propertyName, formatDateOnly(value));
    }
  }

  protected void put(JsonObject representation, String property, JsonObject value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected void put(
    JsonObject representation,
    String property,
    Object check,
    JsonObject value) {

    if(check != null) {
      representation.put(property, value);
    }
  }

  protected void put(JsonObject representation, String property, JsonArray value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  private String formatDateOnly(LocalDate date) {
    return date.toString(DateTimeFormat.forPattern("yyyy-MM-dd"));
  }
}
