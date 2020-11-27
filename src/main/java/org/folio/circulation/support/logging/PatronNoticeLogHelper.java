package org.folio.circulation.support.logging;

import org.folio.circulation.domain.notice.PatronNotice;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.support.http.client.CqlQuery;
import org.folio.circulation.support.http.client.Response;
import org.folio.circulation.support.results.Result;

import io.vertx.core.http.HttpMethod;

public class PatronNoticeLogHelper {

  private PatronNoticeLogHelper() {
    throw new UnsupportedOperationException("Do not instantiate");
  }

  public static void logClientResponse(Result<Response> result, Throwable error, int expectedStatus,
   PatronNotice notice) {

    String successMessage = String.format("Patron notice request sent: %s", notice);
    String errorMessage = String.format("Failed to send patron notice request: %s", notice);

    LogHelper.logClientResponse(result, error, expectedStatus, successMessage, errorMessage);
  }

  public static void logClientResponse(Result<Response> result, Throwable error, int expectedStatus,
    HttpMethod action, ScheduledNotice notice) {

    String successMessage = String.format("%s scheduled patron notice succeeded: %s", action, notice);
    String errorMessage = String.format("%s scheduled patron notice failed: %s", action, notice);

    LogHelper.logClientResponse(result, error, expectedStatus, successMessage, errorMessage);
  }

  public static void logClientResponse(Result<Response> result, Throwable error, int expectedStatus,
    HttpMethod action, CqlQuery cqlQuery) {

    String successMessage = String.format("%s scheduled patron notices by query succeeded: %s",
      action, cqlQuery);
    String errorMessage = String.format("%s scheduled patron notices by query failed: %s",
      action, cqlQuery);

    LogHelper.logClientResponse(result, error, expectedStatus, successMessage, errorMessage);
  }

}
