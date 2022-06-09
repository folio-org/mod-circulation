package org.folio.circulation.support.http.server;

import org.folio.circulation.support.http.client.Response;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;

public class ForwardResponse {
  private static final String CONTENT_TYPE_HEADER = "content-type";
  private static final String CONTENT_LENGTH_HEADER = "content-length";

  private ForwardResponse() { }

  public static void forward(HttpServerResponse forwardTo,
    Response forwardFrom) {

    forwardTo.setStatusCode(forwardFrom.getStatusCode());

    if(forwardFrom.hasBody()) {
      Buffer buffer = Buffer.buffer(forwardFrom.getBody(), "UTF-8");

      forwardTo.putHeader(CONTENT_TYPE_HEADER, forwardFrom.getContentType());
      forwardTo.putHeader(CONTENT_LENGTH_HEADER, Integer.toString(buffer.length()));

      forwardTo.write(buffer);
      forwardTo.end();
    }
    else {
      forwardTo.end();
    }
  }
}
