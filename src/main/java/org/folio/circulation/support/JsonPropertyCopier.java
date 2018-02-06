package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class JsonPropertyCopier {
  private JsonPropertyCopier() {

  }

  public static void copyStringIfExists(
    String propertyName,
    JsonObject from,
    JsonObject to) {

    if(from.containsKey(propertyName)) {
      to.put(propertyName, from.getString(propertyName));
    }
  }
}
