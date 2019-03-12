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


  public static class Builder {

    private String templateId;
    private NoticeFormat noticeFormat;
    private NoticeEventType noticeEventType;
    private NoticeTiming timing;
    private Period timingPeriod;
    private boolean recurring;
    private Period recurringPeriod;

    public Builder setTemplateId(String templateId) {
      this.templateId = templateId;
      return this;
    }

    public Builder setNoticeFormat(NoticeFormat noticeFormat) {
      this.noticeFormat = noticeFormat;
      return this;
    }

    public Builder setNoticeEventType(NoticeEventType noticeEventType) {
      this.noticeEventType = noticeEventType;
      return this;
    }

    public Builder setTiming(NoticeTiming timing) {
      this.timing = timing;
      return this;
    }

    public Builder setTimingPeriod(Period timingPeriod) {
      this.timingPeriod = timingPeriod;
      return this;
    }

    public Builder setRecurring(boolean recurring) {
      this.recurring = recurring;
      return this;
    }

    public Builder setRecurringPeriod(Period recurringPeriod) {
      this.recurringPeriod = recurringPeriod;
      return this;
    }

    public NoticeDescriptor build() {
      return new NoticeDescriptor(
        templateId, noticeFormat, noticeEventType,
        timing, timingPeriod, recurring, recurringPeriod);
    }
  }
}
