package org.folio.circulation.domain.notice.schedule.grouping;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.notice.schedule.ScheduledNotice;
import org.folio.circulation.domain.notice.schedule.TriggeringEvent;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class ScheduledNoticeGroupDefinition {

  private final String userId;
  private final String templateId;
  private final TriggeringEvent triggeringEvent;
  private final NoticeFormat noticeFormat;
  private final NoticeTiming noticeTiming;

  ScheduledNoticeGroupDefinition(ScheduledNotice notice) {
    this(notice.getRecipientUserId(), notice.getConfiguration().getTemplateId(),
      notice.getTriggeringEvent(), notice.getConfiguration().getFormat(),
      notice.getConfiguration().getTiming());
  }
}
