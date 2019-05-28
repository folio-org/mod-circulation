package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;

public class ScheduledNoticeConfig {

  private final NoticeTiming timing;
  private final Long recurringPeriod;
  private final String templateId;
  private final NoticeFormat format;
  private final boolean sendInRealTime;

  public ScheduledNoticeConfig(
    NoticeTiming timing, Long recurringPeriod, String templateId,
    NoticeFormat format, boolean sendInRealTime) {

    this.timing = timing;
    this.recurringPeriod = recurringPeriod;
    this.templateId = templateId;
    this.format = format;
    this.sendInRealTime = sendInRealTime;
  }

  public NoticeTiming getTiming() {
    return timing;
  }

  public long getRecurringPeriod() {
    return isRecurring() ? recurringPeriod : 0;
  }

  public String getTemplateId() {
    return templateId;
  }

  public NoticeFormat getFormat() {
    return format;
  }

  public boolean sendInRealTime() {
    return sendInRealTime;
  }

  public boolean isRecurring() {
    return recurringPeriod != null;
  }
}
