package org.folio.circulation.support;

import org.folio.circulation.support.http.server.ClientErrorResponse;

import io.vertx.core.http.HttpServerResponse;

public class BadRequestFailure implements HttpFailure {
  private final String reason;

  public BadRequestFailure(String reason) {
    this.reason = reason;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ClientErrorResponse.badRequest(response, reason);
  }

  public String getReason() {
    return reason;
  }
}
