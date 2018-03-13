package org.folio.circulation.support;

import io.vertx.core.json.JsonObject;

public abstract class JsonHttpResult implements WritableHttpResult<JsonObject> {
  protected final JsonObject body;

  JsonHttpResult(JsonObject body) {
    this.body = body;
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public JsonObject value() {
    return null;
  }

  @Override
  public HttpFailure cause() {
    return null;
  }
}
