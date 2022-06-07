package org.folio.circulation.support.http.server.response;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.ContentType;

public class ServerErrorResponse {
  private ServerErrorResponse() { }

  public static void internalError(HttpServerResponse response, String reason) {
    response.setStatusCode(500);

    response.putHeader("content-type", ContentType.TEXT_PLAIN);

    if(reason != null) {
      response.end(reason);
    }
    else {
      response.end();
    }
  }
}
