package org.folio.circulation.domain.policy;

public class Period {
  private final Integer duration;
  private final String interval;

  private Period(Integer duration, String interval) {
    this.duration = duration;
    this.interval = interval;
  }

  static Period months(int duration) {
    return from(duration, "Months");
  }

  public static Period weeks(Integer duration) {
    return from(duration, "Weeks");
  }

  public static Period days(Integer duration) {
    return from(duration, "Days");
  }

  static Period hours(int duration) {
    return from(duration, "Hours");
  }

  static Period minutes(int duration) {
    return from(duration, "Minutes");
  }

  public static Period from(Integer duration, String interval) {
    return new Period(duration, interval);
  }

  public Integer getDuration() {
    return duration;
  }

  public String getInterval() {
    return interval;
  }
}
