package org.folio.circulation.support.http.server;

import io.vertx.core.http.HttpServerResponse;

public class NoContentResponse implements HttpResponse {
  public static void noContent(HttpServerResponse response) {
    new NoContentResponse().writeTo(response);
  }

  public NoContentResponse() { }

  @Override
  public void writeTo(HttpServerResponse response) {
    response.setStatusCode(204);
    response.end();
  }
}
