package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class OkJsonResponseResult extends JsonResponseResult {
  public OkJsonResponseResult(JsonObject body) {
    this(body, null);
  }

  public OkJsonResponseResult(JsonObject body, String location) {
    super(200, body, location);
  }

}
