package org.folio.circulation.domain.notice;

import org.joda.time.Period;

public class NoticeDescriptor {

  private String templateId;

  private NoticeFormat noticeFormat;

  private NoticeEventType noticeEventType;

  private NoticeTiming timing;

  private Period timingPeriod;

  private boolean recurring;

  private Period recurringPeriod;

  public NoticeDescriptor(
    String templateId, NoticeFormat noticeFormat, NoticeEventType noticeEventType,
    NoticeTiming timing, Period timingPeriod,
    boolean recurring, Period recurringPeriod) {

    this.templateId = templateId;
    this.noticeFormat = noticeFormat;
    this.noticeEventType = noticeEventType;
    this.timing = timing;
    this.timingPeriod = timingPeriod;
    this.recurring = recurring;
    this.recurringPeriod = recurringPeriod;
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

}
