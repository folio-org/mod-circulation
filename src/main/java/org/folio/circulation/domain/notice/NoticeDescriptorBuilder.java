package org.folio.circulation.domain.notice;

import org.joda.time.Period;

public class NoticeDescriptorBuilder {

  private String templateId;
  private NoticeFormat noticeFormat;
  private NoticeEventType noticeEventType;
  private NoticeTiming timing;
  private Period timingPeriod;
  private boolean recurring;
  private Period recurringPeriod;

  public NoticeDescriptorBuilder setTemplateId(String templateId) {
    this.templateId = templateId;
    return this;
  }

  public NoticeDescriptorBuilder setNoticeFormat(NoticeFormat noticeFormat) {
    this.noticeFormat = noticeFormat;
    return this;
  }

  public NoticeDescriptorBuilder setNoticeEventType(NoticeEventType noticeEventType) {
    this.noticeEventType = noticeEventType;
    return this;
  }

  public NoticeDescriptorBuilder setTiming(NoticeTiming timing) {
    this.timing = timing;
    return this;
  }

  public NoticeDescriptorBuilder setTimingPeriod(Period timingPeriod) {
    this.timingPeriod = timingPeriod;
    return this;
  }

  public NoticeDescriptorBuilder setRecurring(boolean recurring) {
    this.recurring = recurring;
    return this;
  }

  public NoticeDescriptorBuilder setRecurringPeriod(Period recurringPeriod) {
    this.recurringPeriod = recurringPeriod;
    return this;
  }

  public NoticeConfiguration build() {
    return new NoticeConfiguration(
      templateId, noticeFormat, noticeEventType,
      timing, timingPeriod, recurring, recurringPeriod);
  }
}
