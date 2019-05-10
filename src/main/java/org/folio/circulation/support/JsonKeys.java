package org.folio.circulation.support;

import java.util.function.Function;

import io.vertx.core.json.JsonObject;

public class JsonKeys {
  private JsonKeys() { }

  public static Function<JsonObject, String> byId() {
    return record -> record.getString("id");
  }
}
