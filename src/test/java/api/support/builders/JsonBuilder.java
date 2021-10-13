package api.support.builders;

import static org.folio.circulation.support.utils.DateFormatUtil.formatDate;
import static org.folio.circulation.support.utils.DateFormatUtil.formatDateTime;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

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

  protected void put(JsonObject representation, String property, ZonedDateTime value) {
    if(value != null) {
      representation.put(property, formatDateTime(value));
    }
  }

  protected void put(JsonObject representation, String propertyName, LocalDate value) {
    if(value != null) {
      representation.put(propertyName, formatDate(value));
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

  protected <T> void put(JsonObject representation, String property, List<T> value) {
    if(value != null) {
      put(representation, property, new JsonArray(value));
    }
  }

  protected void put(JsonObject representation, String property, JsonArray value) {
    if(value != null) {
      representation.put(property, value);
    }
  }

  protected <T, V> void putIfNotNull(JsonObject representation, String property,
    T value, Function<T, V> toJsonMapper) {

    if(value != null) {
      representation.put(property, toJsonMapper.apply(value));
    }
  }
}
