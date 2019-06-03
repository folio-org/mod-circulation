package org.folio.circulation.domain.notice;


import org.folio.circulation.domain.policy.Period;

public class NoticeConfigurationBuilder {

  private String templateId;
  private NoticeFormat noticeFormat;
  private NoticeEventType noticeEventType;
  private NoticeTiming timing;
  private Period timingPeriod;
  private boolean recurring;
  private Period recurringPeriod;
  private boolean sendInRealTime;

  public NoticeConfigurationBuilder setTemplateId(String templateId) {
    this.templateId = templateId;
    return this;
  }

  public NoticeConfigurationBuilder setNoticeFormat(NoticeFormat noticeFormat) {
    this.noticeFormat = noticeFormat;
    return this;
  }

  public NoticeConfigurationBuilder setNoticeEventType(NoticeEventType noticeEventType) {
    this.noticeEventType = noticeEventType;
    return this;
  }

  public NoticeConfigurationBuilder setTiming(NoticeTiming timing) {
    this.timing = timing;
    return this;
  }

  public NoticeConfigurationBuilder setTimingPeriod(Period timingPeriod) {
    this.timingPeriod = timingPeriod;
    return this;
  }

  public NoticeConfigurationBuilder setRecurring(boolean recurring) {
    this.recurring = recurring;
    return this;
  }

  public NoticeConfigurationBuilder setRecurringPeriod(Period recurringPeriod) {
    this.recurringPeriod = recurringPeriod;
    return this;
  }

  public NoticeConfigurationBuilder setSendInRealTime(boolean sendInRealTime) {
    this.sendInRealTime = sendInRealTime;
    return this;
  }

  public NoticeConfiguration build() {
    return new NoticeConfiguration(
      templateId, noticeFormat, noticeEventType,
      timing, timingPeriod, recurring, recurringPeriod, sendInRealTime);
  }
}
