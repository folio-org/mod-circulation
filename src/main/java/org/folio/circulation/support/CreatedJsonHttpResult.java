package org.folio.circulation.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class CreatedJsonHttpResult extends JsonHttpResult {
  private final String location;

  public CreatedJsonHttpResult(JsonObject body, String location) {
    super(body);
    this.location = location;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    //TODO: Consolidate JsonHttpResult classes and JSONResponse class
    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(201);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));


    if(location != null) {
      response.putHeader("location", location);
    }

    response.write(buffer);
    response.end();
  }

  public static WritableHttpResult<JsonObject> from(HttpResult<JsonObject> result) {
    if(result.failed()) {
      return HttpResult.failure(result.cause());
    }
    else {
      return new CreatedJsonHttpResult(result.value(), null);
    }
  }
}
