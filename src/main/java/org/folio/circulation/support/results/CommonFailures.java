package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.Result.failed;

import lombok.extern.slf4j.Slf4j;
import org.folio.circulation.support.ServerErrorFailure;

@Slf4j
public class CommonFailures {
  private CommonFailures() {}

  public static <T> Result<T> failedDueToServerError(Throwable e) {
    log.error("failedDueToServerError:: Internal server error occurred", e);
    return failed(new ServerErrorFailure(e));
  }

  public static <T> Result<T> failedDueToServerError(String reason) {
    return failed(new ServerErrorFailure(reason));
  }
}
