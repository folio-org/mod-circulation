package org.folio.circulation.domain.notice.schedule.grouping;

import org.folio.circulation.domain.notice.schedule.ScheduledNotice;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
public class SessionAwareScheduledNoticeGroupDefinition extends ScheduledNoticeGroupDefinition {

  private final String sessionId;

  SessionAwareScheduledNoticeGroupDefinition(ScheduledNotice notice) {
    super(notice);
    this.sessionId = notice.getSessionId();
  }

}
