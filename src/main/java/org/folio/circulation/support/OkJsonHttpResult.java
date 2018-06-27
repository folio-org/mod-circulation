package org.folio.circulation.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class OkJsonHttpResult extends JsonHttpResult {
  private final String location;

  public OkJsonHttpResult(JsonObject body) {
    this(body, null);
  }

  public OkJsonHttpResult(JsonObject body, String location) {
    super(body);
    this.location = location;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(200);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    if(StringUtils.isNotBlank(location)) {
      response.putHeader("location", location);
    }

    response.write(buffer);
    response.end();
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failed(result.cause());
    }
    else {
      return new OkJsonHttpResult(result.value());
    }
  }
}
