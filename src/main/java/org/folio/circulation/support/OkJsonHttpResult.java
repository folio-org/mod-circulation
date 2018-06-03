package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.http.server.JsonResponse;

public class OkJsonHttpResult extends JsonHttpResult {
  private final String location;

  private OkJsonHttpResult(JsonObject body) {
    this(body, null);
  }

  public OkJsonHttpResult(JsonObject body, String location) {
    super(body);
    this.location = location;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    JsonResponse.success(response, body, location);
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value());
    }
  }
}
