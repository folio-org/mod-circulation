package org.folio.circulation.domain.notice.schedule;

import io.vertx.core.json.JsonObject;

public class ScheduledNoticeBuilder {

  private JsonObject representation;
  private String id;
  private String loanId;
  private String requestId;
  private long nextRunTime;
  private ScheduledNoticeConfig noticeConfig;

  public ScheduledNoticeBuilder setRepresentation(JsonObject representation) {
    this.representation = representation;
    return this;
  }

  public ScheduledNoticeBuilder setId(String id) {
    this.id = id;
    return this;
  }

  public ScheduledNoticeBuilder setLoanId(String loanId) {
    this.loanId = loanId;
    return this;
  }

  public ScheduledNoticeBuilder setRequestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  public ScheduledNoticeBuilder setNextRunTime(long nextRunTime) {
    this.nextRunTime = nextRunTime;
    return this;
  }

  public ScheduledNoticeBuilder setNoticeConfig(ScheduledNoticeConfig noticeConfig) {
    this.noticeConfig = noticeConfig;
    return this;
  }

  public ScheduledNotice build() {
    return new ScheduledNotice(representation, id, loanId, requestId, nextRunTime, noticeConfig);
  }
}
