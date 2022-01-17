package org.folio.circulation.support;

import java.util.Objects;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.folio.circulation.domain.SideEffectOnFailure;
import org.folio.circulation.support.http.server.ServerErrorResponse;

import io.vertx.core.http.HttpServerResponse;

public class ServerErrorFailure implements HttpFailure {
  public final String reason;

  private SideEffectOnFailure sideEffectOnFailure;

  public boolean isSideEffectOnFailureEqualTo(SideEffectOnFailure sideEffectOnFailure) {
    return Objects.equals(sideEffectOnFailure, this.sideEffectOnFailure);
  }

  public ServerErrorFailure(String reason) {
    this.reason = reason;
  }

  public ServerErrorFailure(Throwable e) {
    this(mapToString(e));
  }

  public static ServerErrorFailure serverErrorFailureWithSideEffect(String reason,
    SideEffectOnFailure sideEffectOnFailure) {
    ServerErrorFailure serverErrorFailure = new ServerErrorFailure(reason);
    serverErrorFailure.sideEffectOnFailure = sideEffectOnFailure;
    return serverErrorFailure;
  }

  public static ServerErrorFailure serverErrorFailureWithSideEffect(Throwable e,
    SideEffectOnFailure sideEffectOnFailure) {
    ServerErrorFailure serverErrorFailure = new ServerErrorFailure(e);
    serverErrorFailure.sideEffectOnFailure = sideEffectOnFailure;
    return serverErrorFailure;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ServerErrorResponse.internalError(response, reason);
  }

  public String getReason() {
    return reason;
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

    return String.format("%s%n%s", reason, ExceptionUtils.getStackTrace(e));
  }

  @Override
  public String toString() {
    return String.format("Server error failure, reason: %s", reason);
  }
}
