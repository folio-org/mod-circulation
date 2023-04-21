package org.folio.circulation.domain;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import lombok.ToString;
import lombok.Value;

@Value
@ToString
public class TimePeriod {
  int duration;
  String intervalId;

  public ChronoUnit getInterval() {
    final String id = getIntervalId();
    if (id != null) {
      return ChronoUnit.valueOf(id.toUpperCase());
    } else {
      // Default is days
      return ChronoUnit.DAYS;
    }
  }

  public long between(ZonedDateTime start, ZonedDateTime end) {
    return getInterval().between(start, end);
  }

  public boolean isLongTermPeriod() {
    final ChronoUnit chronoUnit = getInterval();

    return chronoUnit == ChronoUnit.DAYS || chronoUnit == ChronoUnit.WEEKS
      || chronoUnit == ChronoUnit.MONTHS;
  }

  public Duration getIntervalDuration(){
    return Duration.of(getDuration(),getInterval());
  }
}
