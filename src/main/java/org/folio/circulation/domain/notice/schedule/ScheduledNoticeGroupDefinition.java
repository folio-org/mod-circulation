package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@AllArgsConstructor
public class ScheduledNoticeGroupDefinition {

  private final String userId;
  private final String templateId;
  private final TriggeringEvent triggeringEvent;
  private final NoticeFormat noticeFormat;
  private final NoticeTiming noticeTiming;
  private final String sessionId;

  public static ScheduledNoticeGroupDefinition from(ScheduledNotice notice) {
    ScheduledNoticeConfig config = notice.getConfiguration();

    return new ScheduledNoticeGroupDefinition(notice.getRecipientUserId(), config.getTemplateId(),
      notice.getTriggeringEvent(), config.getFormat(), config.getTiming(), notice.getSessionId());
  }

}
