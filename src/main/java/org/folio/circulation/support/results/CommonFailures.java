package org.folio.circulation.support.results;

import static org.folio.circulation.support.Result.failed;

import org.folio.circulation.support.Result;
import org.folio.circulation.support.ServerErrorFailure;

public class CommonFailures {
  private CommonFailures() {}

  public static <T> Result<T> failedDueToServerError(Throwable e) {
    return failed(new ServerErrorFailure(e));
  }

  public static <T> Result<T> failedDueToServerError(String reason) {
    return failed(new ServerErrorFailure(reason));
  }
}
