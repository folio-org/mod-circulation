package org.folio.circulation.support.logging;

import static org.folio.circulation.support.http.OkapiHeader.REQUEST_ID;
import static org.folio.circulation.support.http.OkapiHeader.TENANT;

import io.vertx.ext.web.RoutingContext;
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

  private static String null2empty(String s) {
    return (s == null) ? "" : s;
  }

  public static void logRequest(RoutingContext rc, Logger logger) {
    if (logger.isInfoEnabled()) {
      logger.info("[{}] [{}] {} {}",
          null2empty(rc.request().getHeader(REQUEST_ID)),
          null2empty(rc.request().getHeader(TENANT)),
          rc.request().method(), rc.request().path());
    }
    rc.next();
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
