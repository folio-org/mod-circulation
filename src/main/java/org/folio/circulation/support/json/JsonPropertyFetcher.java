package org.folio.circulation.support.json;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.circulation.support.utils.DateFormatUtil.parseJodaDate;
import static org.folio.circulation.support.utils.DateFormatUtil.parseDate;
import static org.joda.time.DateTime.parse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.BiFunction;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonPropertyFetcher {
  private JsonPropertyFetcher() { }

  public static String getNestedStringProperty(JsonObject representation,
    String objectName, String propertyName) {

    if (representation == null) {
      return null;
    }

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getString(propertyName)
      : null;
  }

  public static Integer getNestedIntegerProperty(JsonObject representation,
    String objectName, String propertyName) {

    if (representation == null) {
      return null;
    }

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getInteger(propertyName)
      : null;
  }

  public static DateTime getNestedDateTimeProperty(JsonObject representation,
    String objectName, String propertyName) {

    if (representation.containsKey(objectName)) {
      final JsonObject object = representation.getJsonObject(objectName);

      return getDateTimeProperty(object, propertyName);
    } else {
      return null;
    }
  }

  public static JsonObject getNestedObjectProperty(JsonObject representation, String objectName,
    String propertyName) {

    if (representation == null) {
      return null;
    }

    if (representation.containsKey(objectName)) {
      final JsonObject object = representation.getJsonObject(objectName);

      return getObjectProperty(object, propertyName);
    } else {
      return null;
    }
  }

  public static OffsetDateTime getOffsetDateTimeProperty(JsonObject representation,
    String propertyName) {

    if (representation != null && isNotBlank(representation.getString(propertyName))) {
      return OffsetDateTime.parse(representation.getString(propertyName));
    } else {
      return null;
    }
  }

  public static DateTime getDateTimeProperty(JsonObject representation, String propertyName) {
    return getDateTimeProperty(representation, propertyName, null);
  }

  public static DateTime getDateTimeProperty(JsonObject representation, String propertyName,
    DateTime defaultValue) {

    if (representation != null && isNotBlank(representation.getString(propertyName))) {
      return parse(representation.getString(propertyName));
    } else {
      return defaultValue;
    }
  }

  public static LocalDate getLocalDateProperty(JsonObject representation, String propertyName) {
    if (representation != null && representation.containsKey(propertyName)) {
      return parseDate(representation.getString(propertyName));
    } else {
      return null;
    }
  }

  public static org.joda.time.LocalDate getJodaLocalDateProperty(JsonObject representation,
    String propertyName) {

    if (representation != null && representation.containsKey(propertyName)) {
      return parseJodaDate(representation.getString(propertyName), DateTimeZone.UTC);
    } else {
      return null;
    }
  }

  public static UUID getUUIDProperty(JsonObject representation, String propertyName) {
    if (representation != null && representation.containsKey(propertyName)) {
      return UUID.fromString(representation.getString(propertyName));
    } else {
      return null;
    }
  }

  public static JsonObject getObjectProperty(JsonObject representation, String propertyName) {
    if (representation != null) {
      return representation.getJsonObject(propertyName);
    } else {
      return null;
    }
  }

  public static JsonArray getArrayProperty(JsonObject representation, String propertyName) {
    if (representation == null) {
      return new JsonArray();
    }
    JsonArray val = representation.getJsonArray(propertyName);
    return val != null ? val : new JsonArray();
  }

  public static String getProperty(JsonObject representation, String propertyName) {
    if (representation != null) {
      return representation.getString(propertyName);
    } else {
      return null;
    }
  }


  public static boolean getBooleanProperty(JsonObject representation, String propertyName) {
    if (representation != null) {
      return representation.getBoolean(propertyName, false);
    } else {
      return false;
    }
  }

  public static Integer getIntegerProperty(JsonObject representation, String propertyName,
    Integer defaultValue) {

    if (representation != null) {
      return representation.getInteger(propertyName, defaultValue);
    } else {
      return defaultValue;
    }
  }

  public static Double getDoubleProperty(JsonObject representation, String propertyName,
    Double defaultValue) {

    if (representation != null) {
      return representation.getDouble(propertyName, defaultValue);
    } else {
      return defaultValue;
    }
  }

  public static BigDecimal getBigDecimalProperty(JsonObject representation, String propertyName) {
    if (representation != null && representation.getValue(propertyName) != null) {
      // the property can be either a number or a string
      return new BigDecimal(representation.getValue(propertyName).toString());
    } else {
      return null;
    }
  }

  public static void copyProperty(JsonObject from, JsonObject to, String propertyName) {
    if (from == null) {
      return;
    }

    if (to == null) {
      return;
    }

    if (from.containsKey(propertyName)) {
      to.put(propertyName, from.getValue(propertyName));
    }
  }

  public static DateTime getDateTimePropertyByPath(JsonObject from, String... paths) {
    return getByPath(from, JsonPropertyFetcher::getDateTimeProperty, paths);
  }

  private static <T> T getByPath(JsonObject from, BiFunction<JsonObject, String, T> getter,
    String... paths) {

    if (from == null || paths.length == 0) {
      return null;
    }

    JsonObject currentObject = from;

    for (int pathIndex = 0; pathIndex < paths.length - 1; pathIndex++) {
      currentObject = currentObject.getJsonObject(paths[pathIndex], new JsonObject());
    }

    return getter.apply(currentObject, paths[paths.length - 1]);
  }

  public static Object getValueByPath(JsonObject record, String... paths) {
    return getByPath(record, JsonObject::getValue, paths);
  }
}
