package org.folio.circulation.support.logging;

import static java.util.stream.Collectors.toList;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.MultipleRecords;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;
import org.folio.okapi.common.logging.FolioLoggingContext;
import org.folio.rest.RestVerticle;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class LogHelper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  public static final String MODULE_NAME = "mod-circulation";

  private LogHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  private static String null2empty(String s) {
    return (s == null) ? "" : s;
  }

  public static void logRequest(RoutingContext rc, Logger logger) {
    final HttpServerRequest request = rc.request();
    populateLoggingContext(request);

//    if (logger.isInfoEnabled()) {
//      logger.info("[{}] [{}] {} {}",
//          null2empty(rc.request().getHeader(REQUEST_ID)),
//          null2empty(rc.request().getHeader(TENANT)),
//          rc.request().method(), rc.request().path());
//    }

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

  public static <T> String asString(MultipleRecords<T> records, Function<T, String> elementMapper) {
    return asString(records.getRecords(), elementMapper);
  }

  public static <T> String asString(Collection<T> collection, Function<T, String> elementMapper) {
    if (collection == null) {
      return "null collection";
    }

    return String.format("%d object(s): %s",
        collection.size(),
        collection.stream().map(elementMapper).collect(toList()));
  }

  private static void populateLoggingContext(HttpServerRequest request) {
    FolioLoggingContext.put(FolioLoggingContext.TENANT_ID_LOGGING_VAR_NAME, request.getHeader(RestVerticle.OKAPI_HEADER_TENANT));
    FolioLoggingContext.put(FolioLoggingContext.REQUEST_ID_LOGGING_VAR_NAME, request.getHeader(RestVerticle.OKAPI_REQUESTID_HEADER));
    FolioLoggingContext.put(FolioLoggingContext.USER_ID_LOGGING_VAR_NAME,request.getHeader(RestVerticle.OKAPI_USERID_HEADER));
    FolioLoggingContext.put(FolioLoggingContext.MODULE_ID_LOGGING_VAR_NAME, MODULE_NAME);

//    request.endHandler(v -> {
//      FolioLoggingContext.put(FolioLoggingContext.TENANT_ID_LOGGING_VAR_NAME, null);
//      FolioLoggingContext.put(FolioLoggingContext.REQUEST_ID_LOGGING_VAR_NAME, null);
//      FolioLoggingContext.put(FolioLoggingContext.USER_ID_LOGGING_VAR_NAME, null);
//      FolioLoggingContext.put(FolioLoggingContext.MODULE_ID_LOGGING_VAR_NAME, null);
//    });
  }
}
