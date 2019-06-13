package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class CreatedJsonResponseResult extends JsonResponseResult {
  public CreatedJsonResponseResult(JsonObject body, String location) {
    super(201, body, location);
  }

  public static ResponseWritableResult<JsonObject> from(Result<JsonObject> result) {
    if(result.failed()) {
      return Result.failed(result.cause());
    }
    else {
      return new CreatedJsonResponseResult(result.value(), null);
    }
  }
}
