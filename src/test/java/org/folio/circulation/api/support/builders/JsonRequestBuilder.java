package org.folio.circulation.api.support.builders;

import io.vertx.core.json.JsonObject;

public class JsonRequestBuilder {
  protected void put(JsonObject request, String property, String value) {
    if(value != null) {
      request.put(property, value);
    }
  }
}
