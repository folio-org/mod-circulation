package org.folio.circulation.support.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.client.Response;

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
      forwardTo.end();
    }
    else {
      forwardTo.end();
    }
  }

  public static void forward(HttpServerResponse forwardTo,
                             Response forwardFrom) {

    forwardTo.setStatusCode(forwardFrom.getStatusCode());

    if(forwardFrom.hasBody()) {
      Buffer buffer = Buffer.buffer(forwardFrom.getBody(), "UTF-8");

      forwardTo.putHeader("content-type", forwardFrom.getContentType());
      forwardTo.putHeader("content-length", Integer.toString(buffer.length()));

      forwardTo.write(buffer);
      forwardTo.end();
    }
    else {
      forwardTo.end();
    }
  }

}
