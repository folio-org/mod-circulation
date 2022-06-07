package org.folio.circulation.support.http.server.response;

import io.vertx.core.http.HttpServerResponse;

public class NoContentResponse implements HttpResponse {
  public static HttpResponse noContent() {
    return new NoContentResponse();
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response.setStatusCode(204);
    response.end();
  }
}
