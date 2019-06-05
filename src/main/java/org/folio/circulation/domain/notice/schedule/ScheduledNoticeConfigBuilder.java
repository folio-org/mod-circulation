package org.folio.circulation.domain.notice.schedule;

import org.folio.circulation.domain.notice.NoticeFormat;
import org.folio.circulation.domain.notice.NoticeTiming;
import org.folio.circulation.domain.policy.Period;

public class ScheduledNoticeConfigBuilder {

  private NoticeTiming timing;
  private Period recurringPeriod;
  private String templateId;
  private NoticeFormat format;
  private boolean sendInRealTime;

  public ScheduledNoticeConfigBuilder setTiming(NoticeTiming timing) {
    this.timing = timing;
    return this;
  }

  public ScheduledNoticeConfigBuilder setRecurringPeriod(Period recurringPeriod) {
    this.recurringPeriod = recurringPeriod;
    return this;
  }

  public ScheduledNoticeConfigBuilder setTemplateId(String templateId) {
    this.templateId = templateId;
    return this;
  }

  public ScheduledNoticeConfigBuilder setFormat(NoticeFormat format) {
    this.format = format;
    return this;
  }

  public ScheduledNoticeConfigBuilder setSendInRealTime(boolean sendInRealTime) {
    this.sendInRealTime = sendInRealTime;
    return this;
  }

  public ScheduledNoticeConfig build() {
    return new ScheduledNoticeConfig(timing, recurringPeriod, templateId, format, sendInRealTime);
  }

}
