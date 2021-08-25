package org.folio.circulation.support.logging;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LogHelper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private LogHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static void logResponse(Result<Response> result, Throwable error, int expectedStatus,
    String successMessage, String errorMessage) {

    String errorCause = "unknown";

    if (error != null) {
      errorCause = error.getMessage();
    } else if (result != null) {
      if (result.failed() && result.cause() != null) {
        errorCause = result.cause().toString();
      } else if (result.succeeded() && result.value() != null) {
        int statusCode = result.value().getStatusCode();
        if (statusCode == expectedStatus) {
          log.info(successMessage);
          return;
        } else {
          errorCause = String.format("[%d] %s", statusCode, result.value().getBody());
        }
      }
    }

    log.error("{}. Cause: {}", errorMessage, errorCause);
  }
}
