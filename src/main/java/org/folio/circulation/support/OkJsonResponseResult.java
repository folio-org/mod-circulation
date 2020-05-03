package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class OkJsonResponseResult extends JsonResponseResult {
  public OkJsonResponseResult(JsonObject body) {
    super(200, body, null);
  }
}
