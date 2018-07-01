package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class CreatedJsonHttpResult extends JsonHttpResult {
  public CreatedJsonHttpResult(JsonObject body, String location) {
    super(201, body, location);
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new CreatedJsonHttpResult(result.value(), null);
    }
  }
}
