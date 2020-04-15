package org.folio.circulation.domain.notice.schedule;

import org.joda.time.DateTime;

public class ScheduledNotice {
  private final String id;
  private final TriggeringEvent triggeringEvent;
  private final DateTime nextRunTime;
  private final ScheduledNoticeConfig configuration;
  private final ReferencedIds referencedIds;

  public ScheduledNotice(String id,
    TriggeringEvent triggeringEvent,
    DateTime nextRunTime,
    ScheduledNoticeConfig configuration,
    ReferencedIds referencedIds) {

    this.id = id;
    this.triggeringEvent = triggeringEvent;
    this.nextRunTime = nextRunTime;
    this.configuration = configuration;
    this.referencedIds = referencedIds;
  }

  public String getId() {
    return id;
  }

  public String getLoanId() {
    return referencedIds.loanId;
  }

  public String getRequestId() {
    return referencedIds.requestId;
  }

  public String getFeeFineActionId() {
    return referencedIds.feeFineActionId;
  }

  public String getRecipientUserId() {
    return referencedIds.userId;
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
    return new ScheduledNotice(id, triggeringEvent,
      nextRunTime, configuration, referencedIds);
  }

  public static class ReferencedIds {
    private final String userId;
    private final String loanId;
    private final String requestId;
    private final String feeFineActionId;

    public ReferencedIds(String userId, String loanId, String requestId, String feeFineActionId) {
      this.userId = userId;
      this.loanId = loanId;
      this.requestId = requestId;
      this.feeFineActionId = feeFineActionId;
    }
  }
}
