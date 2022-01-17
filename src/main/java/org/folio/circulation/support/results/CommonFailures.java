package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.Result.failed;

import org.folio.circulation.domain.SideEffectOnFailure;
import org.folio.circulation.support.ServerErrorFailure;

public class CommonFailures {
  private CommonFailures() {}

  public static <T> Result<T> failedDueToServerError(Throwable e) {
    return failed(new ServerErrorFailure(e));
  }

  public static <T> Result<T> failedDueToServerErrorFailureWithSideEffect(Throwable e,
    SideEffectOnFailure sideEffectOnFailure) {
    return failed(ServerErrorFailure.serverErrorFailureWithSideEffect(e, sideEffectOnFailure));
  }

  public static <T> Result<T> failedDueToServerErrorFailureWithSideEffect(String reason,
    SideEffectOnFailure sideEffectOnFailure) {
    return failed(ServerErrorFailure.serverErrorFailureWithSideEffect(reason, sideEffectOnFailure));
  }

  public static <T> Result<T> failedDueToServerError(String reason) {
    return failed(new ServerErrorFailure(reason));
  }
}
