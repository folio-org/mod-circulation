package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.JsonResponse;

public class CreatedJsonHttpResult extends JsonHttpResult {
  private CreatedJsonHttpResult(JsonObject body) {
    super(body);
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    JsonResponse.created(response, body);
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new CreatedJsonHttpResult(result.value());
    }
  }
}
