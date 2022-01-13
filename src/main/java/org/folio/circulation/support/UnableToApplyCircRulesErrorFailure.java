package org.folio.circulation.support;

import org.folio.circulation.support.http.server.ServerErrorResponse;

import io.vertx.core.http.HttpServerResponse;

public class UnableToApplyCircRulesErrorFailure implements HttpFailure {
  public final String reason;

  public static final String UNABLE_TO_APPLY = "Unable to apply circulation rules for ";

  public UnableToApplyCircRulesErrorFailure(String reason) {
    this.reason = reason;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ServerErrorResponse.internalError(response, UNABLE_TO_APPLY + reason);
  }

  @Override
  public String toString() {
    return String.format("Unable to apply circulation rules error failure, reason: %s", reason);
  }
}
