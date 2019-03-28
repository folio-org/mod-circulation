package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class OkJsonResponseResult extends JsonResponseResult {
  public OkJsonResponseResult(JsonObject body) {
    this(body, null);
  }

  public OkJsonResponseResult(JsonObject body, String location) {
    super(200, body, location);
  }

  public static ResponseWritableResult<JsonObject> from(Result<JsonObject> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new OkJsonResponseResult(result.value());
    }
  }
}
