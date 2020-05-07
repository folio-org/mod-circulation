package org.folio.circulation.support;

import org.folio.circulation.support.http.server.HttpResponse;
import org.folio.circulation.support.http.server.JsonHttpResponse;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

public class JsonResponseResult implements ResponseWritableResult<JsonObject> {
  private final JsonObject body;
  private final HttpResponse httpResponse;

  public JsonResponseResult(int statusCode, JsonObject body, String location) {
    httpResponse = new JsonHttpResponse(statusCode, body, location);
    this.body = body;
  }

  @Override
  public boolean failed() {
    return false;
  }

  @Override
  public JsonObject value() {
    return body;
  }

  @Override
  public HttpFailure cause() {
    return null;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    httpResponse.writeTo(response);
  }
}
