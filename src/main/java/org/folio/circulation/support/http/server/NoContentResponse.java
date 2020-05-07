package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpServerResponse;

public class NoContentResponse implements HttpResponse {
  private final int statusCode;

  public static void noContent(HttpServerResponse response) {
    new NoContentResponse(204).writeTo(response);
  }

  private NoContentResponse(int statusCode) {
    this.statusCode = statusCode;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response.setStatusCode(statusCode);
    response.end();
  }
}
