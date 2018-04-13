package org.folio.circulation.support;

import io.vertx.core.http.HttpServerResponse;
import org.folio.circulation.support.http.server.ServerErrorResponse;

public class ServerErrorFailure implements HttpFailure {
  public final String reason;

  public ServerErrorFailure(String reason) {
    this.reason = reason;
  }

  public ServerErrorFailure(Throwable e) {
    this(mapToString(e));
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ServerErrorResponse.internalError(response, reason);
  }

  private static String mapToString(Throwable e) {
    final String reason;

    if(e.getMessage() != null) {
      reason = e.getMessage();
    }
    else if(e.toString() != null) {
      reason = e.toString();
    }
    else {
      reason = "Unknown internal error";
    }

    return reason;
  }

  @Override
  public String toString() {
    return String.format("Server error failure, reason: %s", reason);
  }
}
