package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;
import org.joda.time.DateTime;

public class JsonPropertyFetcher {
  private JsonPropertyFetcher() {

  }

  public static String getNestedStringProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {

    if(representation == null) {
      return null;
    }

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getString(propertyName)
      : null;
  }

  public static Integer getNestedIntegerProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {

    if(representation == null) {
      return null;
    }

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getInteger(propertyName)
      : null;
  }

  public static DateTime getNestedDateTimeProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {

    if (representation.containsKey(objectName)) {
      final JsonObject object = representation.getJsonObject(objectName);

      return getDateTimeProperty(object, propertyName);
    }
    else {
      return null;
    }
  }

  public static DateTime getDateTimeProperty(
    JsonObject representation,
    String propertyName) {

    if (representation.containsKey(propertyName)) {
      return DateTime.parse(
        representation.getString(propertyName));
    }
    else {
      return null;
    }
  }
}
