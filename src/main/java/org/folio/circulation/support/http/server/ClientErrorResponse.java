package org.folio.circulation.support.http.server;

import org.apache.http.entity.ContentType;

import io.vertx.core.http.HttpServerResponse;

public class ClientErrorResponse {
  private ClientErrorResponse() { }

  public static void notFound(HttpServerResponse response, String text) {
    response.setStatusCode(404);
    response.end(text);
  }

  public static void notFound(HttpServerResponse response) {
    notFound(response, "Not Found");
  }

  public static void badRequest(HttpServerResponse response, String reason) {
    response.setStatusCode(400);
    response.putHeader("content-type", ContentType.TEXT_PLAIN.toString());
    response.end(reason);
  }
}
