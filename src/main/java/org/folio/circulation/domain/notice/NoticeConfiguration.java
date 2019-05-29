package org.folio.circulation.domain.notice;

import org.joda.time.Period;

public class NoticeConfiguration {

  private final String templateId;
  private final NoticeFormat noticeFormat;
  private final NoticeEventType noticeEventType;
  private final NoticeTiming timing;
  private final Period timingPeriod;
  private final boolean recurring;
  private final Period recurringPeriod;
  private final boolean sendInRealTime;

  @SuppressWarnings("squid:S00107")
  NoticeConfiguration(
    String templateId, NoticeFormat noticeFormat, NoticeEventType noticeEventType,
    NoticeTiming timing, Period timingPeriod,
    boolean recurring, Period recurringPeriod, boolean sendInRealTime) {

    this.templateId = templateId;
    this.noticeFormat = noticeFormat;
    this.noticeEventType = noticeEventType;
    this.timing = timing;
    this.timingPeriod = timingPeriod;
    this.recurring = recurring;
    this.recurringPeriod = recurringPeriod;
    this.sendInRealTime = sendInRealTime;
  }

  public String getTemplateId() {
    return templateId;
  }

  public NoticeFormat getNoticeFormat() {
    return noticeFormat;
  }

  public NoticeEventType getNoticeEventType() {
    return noticeEventType;
  }

  public NoticeTiming getTiming() {
    return timing;
  }

  public Period getTimingPeriod() {
    return timingPeriod;
  }

  public boolean isRecurring() {
    return recurring;
  }

  public Period getRecurringPeriod() {
    return recurringPeriod;
  }

  public boolean sendInRealTime() {
    return sendInRealTime;
  }
}
