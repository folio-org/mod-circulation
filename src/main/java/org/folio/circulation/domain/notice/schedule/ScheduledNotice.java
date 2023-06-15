package org.folio.circulation.domain.notice.schedule;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.With;

@ToString
@AllArgsConstructor
@Getter
public class ScheduledNotice {
  private final String id;
  private final String loanId;
  private final String requestId;
  private final String recipientUserId;
  private final String feeFineActionId;
  private final String sessionId;
  private final TriggeringEvent triggeringEvent;
  @With
  private final ZonedDateTime nextRunTime;
  private final ScheduledNoticeConfig configuration;
}
