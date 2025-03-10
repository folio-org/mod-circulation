package org.folio.circulation.support.results;

import static org.folio.circulation.support.results.Result.failed;

import org.folio.circulation.support.ServerErrorFailure;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.invoke.MethodHandles;

public class CommonFailures {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private CommonFailures() {}

  public static <T> Result<T> failedDueToServerError(Throwable e) {
    log.error("Something went wrong", e);
    return failed(new ServerErrorFailure(e));
  }

  public static <T> Result<T> failedDueToServerError(String reason) {
    log.error("Something went wrong", new Throwable(reason));
    return failed(new ServerErrorFailure(reason));
  }
}
