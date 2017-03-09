package org.folio.circulation.support.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerResponse;

public class ForwardResponse {

  public static void forward(HttpServerResponse forwardTo,
                             HttpClientResponse forwardFrom,
                             String responseBody) {

    forwardTo.setStatusCode(forwardFrom.statusCode());

    if(responseBody != null && responseBody.trim() != "") {
      Buffer buffer = Buffer.buffer(responseBody, "UTF-8");

      forwardTo.putHeader("content-type", forwardFrom.getHeader("content-type"));
      forwardTo.putHeader("content-length", Integer.toString(buffer.length()));

      forwardTo.write(buffer);
    }
    else {
      forwardTo.end();
    }
  }
}
