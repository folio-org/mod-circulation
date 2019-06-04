package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.Period;

public class ScheduledNoticeConfig {

  private final NoticeTiming timing;
  private final Period recurringPeriod;
  private final String templateId;
  private final NoticeFormat format;
  private final boolean sendInRealTime;

  public ScheduledNoticeConfig(
    NoticeTiming timing, Period recurringPeriod, String templateId,
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

  public Period getRecurringPeriod() {
    return recurringPeriod;
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
