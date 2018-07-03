package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public class OkJsonHttpResult extends JsonHttpResult {
  public OkJsonHttpResult(JsonObject body) {
    this(body, null);
  }

  public OkJsonHttpResult(JsonObject body, String location) {
    super(200, body, location);
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value());
    }
  }

  public static WritableHttpResult<JsonObject> fromMultiple(
    HttpResult<MultipleRecordsWrapper> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value().toJson());
    }
  }
}
