package org.folio.circulation.support;

import org.folio.circulation.domain.notice.schedule.ScheduledNoticeContext;
import org.folio.circulation.support.http.server.ClientErrorResponse;

import io.vertx.core.http.HttpServerResponse;

public class RecordNotFoundFailure implements HttpFailure {
  private final String recordType;
  private final String id;
  private final ScheduledNoticeContext scheduledNoticeContext;

  public RecordNotFoundFailure(String recordType, String id) {
    this.recordType = recordType;
    this.id = id;
    this.scheduledNoticeContext = null;
  }

  public RecordNotFoundFailure(String recordType, String id, ScheduledNoticeContext scheduledNoticeContext) {
    this.recordType = recordType;
    this.id = id;
    this.scheduledNoticeContext = scheduledNoticeContext;
  }

  public String getRecordType() {
    return recordType;
  }

  public ScheduledNoticeContext getScheduledNoticeContext() {
    return scheduledNoticeContext;
  }

  @Override
  public void writeTo(HttpServerResponse response) {
    ClientErrorResponse.notFound(response, toString());
  }

  @Override
  public String toString() {
    return String.format("%s record with ID \"%s\" cannot be found",
      recordType, id);
  }
}
