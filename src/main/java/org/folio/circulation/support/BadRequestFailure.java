package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.ClientErrorResponse;

public class BadRequestFailure implements HttpFailure {
  private final String reason;

  public BadRequestFailure(String reason) {
    this.reason = reason;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ClientErrorResponse.badRequest(response, reason);
  }
}
