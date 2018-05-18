package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class DefensiveJsonPropertyFetcher {
  private DefensiveJsonPropertyFetcher() {

  }

  public static String getNestedStringProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getString(propertyName)
      : null;
  }

  public static Integer getNestedIntegerProperty(
    JsonObject representation,
    String objectName,
    String propertyName) {

    return representation.containsKey(objectName)
      ? representation.getJsonObject(objectName).getInteger(propertyName)
      : null;
  }
}
