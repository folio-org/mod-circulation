package org.folio.circulation.domain.notice.schedule;

import java.time.ZonedDateTime;

public class ScheduledNoticeBuilder {
  private String id;
  private String loanId;
  private String requestId;
  private String feeFineActionId;
  private String recipientUserId;
  private String sessionId;
  private TriggeringEvent triggeringEvent;
  private ZonedDateTime nextRunTime;
  private ScheduledNoticeConfig noticeConfig;

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

  public ScheduledNoticeBuilder setFeeFineActionId(String feeFineActionId) {
    this.feeFineActionId = feeFineActionId;
    return this;
  }

  public ScheduledNoticeBuilder setRecipientUserId(String recipientUserId) {
    this.recipientUserId = recipientUserId;
    return this;
  }

  public ScheduledNoticeBuilder setNextRunTime(ZonedDateTime nextRunTime) {
    this.nextRunTime = nextRunTime;
    return this;
  }

  public ScheduledNoticeBuilder setNoticeConfig(ScheduledNoticeConfig noticeConfig) {
    this.noticeConfig = noticeConfig;
    return this;
  }

  public ScheduledNoticeBuilder setTriggeringEvent(TriggeringEvent triggeringEvent) {
    this.triggeringEvent = triggeringEvent;
    return this;
  }

  public ScheduledNoticeBuilder setSessionId(String sessionId) {
    this.sessionId = sessionId;
    return this;
  }

  public ScheduledNotice build() {
    return new ScheduledNotice(id, loanId, requestId, recipientUserId, feeFineActionId,
      sessionId, triggeringEvent, nextRunTime, noticeConfig);
  }
}
