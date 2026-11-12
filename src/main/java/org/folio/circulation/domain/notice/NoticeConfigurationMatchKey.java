package org.folio.circulation.domain.notice;

public record NoticeConfigurationMatchKey(
  NoticeEventType noticeEventType,
  NoticeTiming timing,
  Long timingPeriodDuration,
  String timingPeriodInterval,
  boolean recurring,
  Long recurringPeriodDuration,
  String recurringPeriodInterval,
  boolean sendInRealTime) {

  public static NoticeConfigurationMatchKey from(NoticeConfiguration configuration) {
    var timingPeriod = configuration.getTimingPeriod();
    var recurringPeriod = configuration.getRecurringPeriod();

    return new NoticeConfigurationMatchKey(
      configuration.getNoticeEventType(),
      configuration.getTiming(),
      timingPeriod == null ? null : timingPeriod.getDuration(),
      timingPeriod == null ? null : timingPeriod.getInterval(),
      configuration.isRecurring(),
      recurringPeriod == null ? null : recurringPeriod.getDuration(),
      recurringPeriod == null ? null : recurringPeriod.getInterval(),
      configuration.sendInRealTime());
  }
}
