package org.folio.circulation.domain.notice.schedule;

import java.time.ZonedDateTime;

import lombok.ToString;

@ToString
public class ScheduledNotice {
  private final String id;
  private final String loanId;
  private final String requestId;
  private final String recipientUserId;
  private final String feeFineActionId;
  private final TriggeringEvent triggeringEvent;
  private final ZonedDateTime nextRunTime;
  private final ScheduledNoticeConfig configuration;

  @SuppressWarnings({"squid:S00107"}) //too many parameters
  public ScheduledNotice(String id, String loanId, String requestId, String recipientUserId,
    String feeFineActionId, TriggeringEvent triggeringEvent, ZonedDateTime nextRunTime,
    ScheduledNoticeConfig configuration) {

    this.id = id;
    this.loanId = loanId;
    this.requestId = requestId;
    this.recipientUserId = recipientUserId;
    this.feeFineActionId = feeFineActionId;
    this.triggeringEvent = triggeringEvent;
    this.nextRunTime = nextRunTime;
    this.configuration = configuration;
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

  public String getRecipientUserId() {
    return recipientUserId;
  }

  public String getFeeFineActionId() {
    return feeFineActionId;
  }

  public TriggeringEvent getTriggeringEvent() {
    return triggeringEvent;
  }

  public ZonedDateTime getNextRunTime() {
    return nextRunTime;
  }

  public ScheduledNoticeConfig getConfiguration() {
    return configuration;
  }

  public ScheduledNotice withNextRunTime(ZonedDateTime nextRunTime) {
    return new ScheduledNotice(id, loanId, requestId, recipientUserId, feeFineActionId,
      triggeringEvent, nextRunTime, configuration);
  }
}
