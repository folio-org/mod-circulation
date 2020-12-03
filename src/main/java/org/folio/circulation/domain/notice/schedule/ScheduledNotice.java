package org.folio.circulation.domain.notice.schedule;

import org.joda.time.DateTime;

public class ScheduledNotice {
  private final String id;
  private final String loanId;
  private final String requestId;
  private final String recipientUserId;
  private final String feeFineActionId;
  private final TriggeringEvent triggeringEvent;
  private final DateTime nextRunTime;
  private final ScheduledNoticeConfig configuration;

  @SuppressWarnings({"squid:S00107"}) //too many parameters
  public ScheduledNotice(String id, String loanId, String requestId, String recipientUserId,
    String feeFineActionId, TriggeringEvent triggeringEvent, DateTime nextRunTime,
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

  public DateTime getNextRunTime() {
    return nextRunTime;
  }

  public ScheduledNoticeConfig getConfiguration() {
    return configuration;
  }

  public ScheduledNotice withNextRunTime(DateTime nextRunTime) {
    return new ScheduledNotice(id, loanId, requestId, recipientUserId, feeFineActionId,
      triggeringEvent, nextRunTime, configuration);
  }

  @Override
  public String toString() {
    return "ScheduledNotice{" +
      "id='" + id + '\'' +
      ", loanId='" + loanId + '\'' +
      ", requestId='" + requestId + '\'' +
      ", recipientUserId='" + recipientUserId + '\'' +
      ", feeFineActionId='" + feeFineActionId + '\'' +
      ", triggeringEvent='" + triggeringEvent.getRepresentation() + '\'' +
      ", nextRunTime=" + nextRunTime +
      ", configuration=" + configuration +
      '}';
  }
}
