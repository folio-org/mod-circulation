package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.JsonResponse;

public class CreatedHttpResult implements WritableHttpResult<JsonObject> {
  private final JsonObject body;

  public CreatedHttpResult(JsonObject body) {
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

  @Override
  public void writeTo(HttpServerResponse response) {
    JsonResponse.created(response, body);
  }

  public static  WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new CreatedHttpResult(result.value());
    }
  }
}
