package org.folio.circulation.support;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class VertxCollectionResource {

  private final String rootPath;

  public VertxCollectionResource(String rootPath) {

    this.rootPath = rootPath;
  }

  public void register(Router router) {
    router.post(rootPath + "*").handler(BodyHandler.create());
    router.post(rootPath).handler(this::create);
  }

  private void create(RoutingContext routingContext) {
    jsonResponse(routingContext.response(), routingContext.getBodyAsJson(),
      201);
  }

  private static void jsonResponse(HttpServerResponse response,
                                   JsonObject body,
                                   Integer status) {

    String json = Json.encodePrettily(body);
    Buffer buffer = Buffer.buffer(json, "UTF-8");

    response.setStatusCode(status);
    response.putHeader("content-type", "application/json; charset=utf-8");
    response.putHeader("content-length", Integer.toString(buffer.length()));

    response.write(buffer);
    response.end();
  }
}
