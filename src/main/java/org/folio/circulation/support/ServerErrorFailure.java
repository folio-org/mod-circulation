package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

public class ServerErrorFailure implements HttpFailure {
  private final String reason;

  public ServerErrorFailure(String reason) {
    this.reason = reason;
  }

  public ServerErrorFailure(Throwable e) {
    this(e.toString());
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ServerErrorResponse.internalError(response, reason);
  }
}
