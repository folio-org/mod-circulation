package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.Result.failed;

import org.folio.circulation.support.ServerErrorFailure;
import org.folio.circulation.support.UnableToApplyCircRulesErrorFailure;

public class CommonFailures {
  private CommonFailures() {}

  public static <T> Result<T> failedDueToServerError(Throwable e) {
    return failed(new ServerErrorFailure(e));
  }

  public static <T> Result<T> failedDueToServerError(String reason) {
    return failed(new ServerErrorFailure(reason));
  }

  public static <T> Result<T> failedDueToUnableToApplyCircRulesErrorFailure(String reason) {
    return failed(new UnableToApplyCircRulesErrorFailure(reason));
  }
}
