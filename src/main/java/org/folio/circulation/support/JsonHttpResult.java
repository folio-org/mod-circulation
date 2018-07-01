package org.folio.circulation.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class JsonHttpResult implements WritableHttpResult<JsonObject> {
  private final int statusCode;
  private final JsonObject body;
  private final String location;

  public JsonHttpResult(int statusCode, JsonObject body, String location) {
    this.body = body;
    this.location = location;
    this.statusCode = statusCode;
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
    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(statusCode);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    if(StringUtils.isNotBlank(location)) {
      response.putHeader("location", location);
    }

    response.write(buffer);
    response.end();
  }
}
