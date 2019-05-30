package org.folio.circulation.domain.notice.schedule;

import io.vertx.core.json.JsonObject;

import org.joda.time.DateTime;

public class ScheduledNotice {

  private final JsonObject representation;
  private final String id;
  private final String loanId;
  private final String requestId;
  private final DateTime nextRunTime;
  private final ScheduledNoticeConfig noticeConfig;

  public ScheduledNotice(JsonObject representation, String id, String loanId, String requestId,
                         DateTime nextRunTime, ScheduledNoticeConfig noticeConfig) {
    this.representation = representation;
    this.id = id;
    this.loanId = loanId;
    this.requestId = requestId;
    this.nextRunTime = nextRunTime;
    this.noticeConfig = noticeConfig;
  }

  public JsonObject getRepresentation() {
    return representation;
  }

  public String getId() {
    return id;
  }

  public String getLoanId() {
    return loanId;
  }

  public String getRequestId() {
    return requestId;
  }

  public DateTime getNextRunTime() {
    return nextRunTime;
  }

  public ScheduledNoticeConfig getNoticeConfig() {
    return noticeConfig;
  }
}
