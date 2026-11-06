package org.folio.circulation.support;

import org.folio.circulation.support.http.ContentType;

import io.vertx.core.http.HttpServerResponse;

public class ServiceUnavailableFailure implements HttpFailure {
  private final String reason;

  public ServiceUnavailableFailure(String reason) {
    this.reason = reason;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    response.setStatusCode(503)
      .putHeader("content-type", ContentType.TEXT_PLAIN)
      .end(reason);
  }

  public String getReason() {
    return reason;
  }

  @Override
  public String toString() {
    return "Service unavailable failure, reason: " + reason;
  }
}
