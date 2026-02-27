package org.folio.circulation.support.http.server;

import static io.vertx.core.buffer.Buffer.buffer;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonHttpResponse implements HttpResponse {
  private final int statusCode;
  private final JsonObject body;
  private final String location;

  public static HttpResponse ok(JsonObject body, String location) {
    return new JsonHttpResponse(200, body, location);
  }

  public static HttpResponse ok(JsonObject body) {
    System.out.println("building ok response");
    return ok(body, null);
  }

  public static HttpResponse created(JsonObject body, String location) {
    return new JsonHttpResponse(201, body, location);
  }

  public static HttpResponse created(JsonObject body) {
    return created(body, null);
  }

  public static HttpResponse unprocessableEntity(JsonObject body) {
    return new JsonHttpResponse(422, body, null);
  }

  public JsonHttpResponse(int statusCode, JsonObject body, String location) {
    this.statusCode = statusCode;
    this.body = body;
    this.location = location;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    log.info(System.currentTimeMillis() + ": writeTo start in thread " + Thread.currentThread().getName());
    String json = Json.encodePrettily(body);
    log.info(System.currentTimeMillis() + ": response serialized in thread " + Thread.currentThread().getName());
    Buffer buffer = buffer(json, "UTF-8");

    response.setStatusCode(statusCode);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    if(isNotBlank(location)) {
      response.putHeader("location", location);
    }

    log.info(System.currentTimeMillis() + ": start writing buffer in thread " + Thread.currentThread().getName());
    response.write(buffer);
    log.info(System.currentTimeMillis() + ": finished writing buffer in thread " + Thread.currentThread().getName());
    response.end();
    log.info(System.currentTimeMillis() + ": end response in thread " + Thread.currentThread().getName());
  }
}
