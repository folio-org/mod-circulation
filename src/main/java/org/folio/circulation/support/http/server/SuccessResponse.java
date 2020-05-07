package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpServerResponse;

public class SuccessResponse implements HttpResponse {
  private final int statusCode;

  private SuccessResponse(int statusCode) {
    this.statusCode = statusCode;
  }

  public static void noContent(HttpServerResponse response) {
    new SuccessResponse(204).writeTo(response);
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response.setStatusCode(statusCode);
    response.end();
  }
}
