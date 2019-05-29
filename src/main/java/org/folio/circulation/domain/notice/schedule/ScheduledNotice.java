package org.folio.circulation.domain.notice.schedule;

import io.vertx.core.json.JsonObject;

public class ScheduledNotice {

  private final JsonObject representation;
  private final String id;
  private final String loanId;
  private final String requestId;
  private final long nextRunTime;
  private final ScheduledNoticeConfig noticeConfig;

  public ScheduledNotice(JsonObject representation, String id, String loanId, String requestId,
                         long nextRunTime, ScheduledNoticeConfig noticeConfig) {
    this.representation = representation;
    this.id = id;
    this.loanId = loanId;
    this.requestId = requestId;
    this.nextRunTime = nextRunTime;
    this.noticeConfig = noticeConfig;
  }

  public static ScheduledNotice from(JsonObject representation) {
    return new ScheduledNotice(representation, null, null, null, 0, null);
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

  public long getNextRunTime() {
    return nextRunTime;
  }

  public ScheduledNoticeConfig getNoticeConfig() {
    return noticeConfig;
  }
}
