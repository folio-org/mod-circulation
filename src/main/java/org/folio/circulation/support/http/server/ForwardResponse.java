package org.folio.circulation.support.http.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerResponse;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.support.http.client.Response;

public class ForwardResponse {
  private static final String CONTENT_TYPE_HEADER = "content-type";
  private static final String CONTENT_LENGTH_HEADER = "content-length";

  private ForwardResponse() { }

  public static void forward(HttpServerResponse forwardTo,
                             HttpClientResponse forwardFrom,
                             String responseBody) {

    forwardTo.setStatusCode(forwardFrom.statusCode());

    if(StringUtils.isNotBlank(responseBody)) {
      Buffer buffer = Buffer.buffer(responseBody, "UTF-8");

      forwardTo.putHeader(CONTENT_TYPE_HEADER, forwardFrom.getHeader(CONTENT_TYPE_HEADER));
      forwardTo.putHeader(CONTENT_LENGTH_HEADER, Integer.toString(buffer.length()));

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
