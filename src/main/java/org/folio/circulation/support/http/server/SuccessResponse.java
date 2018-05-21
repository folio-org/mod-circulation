package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpServerResponse;

public class SuccessResponse {
  private SuccessResponse() { }

  public static void noContent(HttpServerResponse response) {

    response.setStatusCode(204);
    response.end();
  }
}
