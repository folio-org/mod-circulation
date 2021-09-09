package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import io.vertx.core.json.JsonObject;

public class TimePeriod {
  private final int duration;
  private final String intervalId;

  public TimePeriod(int duration, String intervalId) {
    this.duration = duration;
    this.intervalId = intervalId;
  }

  public int getDuration() {
    return duration;
  }

  public String getIntervalId() {
    return intervalId;
  }

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

  public static TimePeriod from(JsonObject representation) {
    final int duration = getIntegerProperty(representation, "duration", 0);
    final String intervalId = getProperty(representation, "intervalId");

    return new TimePeriod(duration, intervalId);
  }
}
