package org.folio.circulation.domain.notice.schedule;

import org.joda.time.DateTime;

public class ScheduledNotice {

  private final String id;
  private final String loanId;
  private final String requestId;
  private final DateTime nextRunTime;
  private final ScheduledNoticeConfig configuration;

  public ScheduledNotice(String id, String loanId, String requestId,
                         DateTime nextRunTime, ScheduledNoticeConfig configuration) {
    this.id = id;
    this.loanId = loanId;
    this.requestId = requestId;
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

  public DateTime getNextRunTime() {
    return nextRunTime;
  }

  public ScheduledNoticeConfig getConfiguration() {
    return configuration;
  }

  public ScheduledNotice withNextRunTime(DateTime nextRunTime) {
    return new ScheduledNotice(id, loanId, requestId, nextRunTime, configuration);
  }
}
