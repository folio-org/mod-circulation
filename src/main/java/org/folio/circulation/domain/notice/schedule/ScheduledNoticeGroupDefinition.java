package org.folio.circulation.domain.notice.schedule;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;

public class ScheduledNoticeGroupDefinition {

  public static ScheduledNoticeGroupDefinition from(ScheduledNotice notice) {
    ScheduledNoticeConfig config = notice.getConfiguration();

    return new ScheduledNoticeGroupDefinition(notice.getUserId(), config.getTemplateId(),
      notice.getTriggeringEvent(), config.getFormat(), config.getTiming());
  }

  private final String userId;
  private final String templateId;
  private final TriggeringEvent triggeringEvent;
  private final NoticeFormat noticeFormat;
  private final NoticeTiming noticeTiming;


  public ScheduledNoticeGroupDefinition(String userId, String templateId,
                                        TriggeringEvent triggeringEvent,
                                        NoticeFormat noticeFormat,
                                        NoticeTiming noticeTiming) {
    this.userId = userId;
    this.templateId = templateId;
    this.triggeringEvent = triggeringEvent;
    this.noticeFormat = noticeFormat;
    this.noticeTiming = noticeTiming;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    ScheduledNoticeGroupDefinition that = (ScheduledNoticeGroupDefinition) o;

    return new EqualsBuilder()
      .append(userId, that.userId)
      .append(templateId, that.templateId)
      .append(triggeringEvent, that.triggeringEvent)
      .append(noticeFormat, that.noticeFormat)
      .append(noticeTiming, that.noticeTiming)
      .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
      .append(userId)
      .append(templateId)
      .append(triggeringEvent)
      .append(noticeFormat)
      .append(noticeTiming)
      .toHashCode();
  }
}
