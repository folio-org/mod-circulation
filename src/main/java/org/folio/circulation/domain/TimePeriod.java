package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.utils.DateTimeUtil.toJavaDateTime;

import java.time.temporal.ChronoUnit;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class TimePeriod {
  private final JsonObject representation;

  public TimePeriod(JsonObject representation) {
    this.representation = representation;
  }

  public Integer getDuration() {
    return getIntegerProperty(representation, "duration", null);
  }

  public String getIntervalId() {
    return getProperty(representation, "intervalId");
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

  public long between(DateTime start, DateTime end) {
    return getInterval().between(toJavaDateTime(start), toJavaDateTime(end));
  }

  public boolean isLongTermPeriod() {
    final ChronoUnit chronoUnit = getInterval();

    return chronoUnit == ChronoUnit.DAYS || chronoUnit == ChronoUnit.WEEKS
      || chronoUnit == ChronoUnit.MONTHS;
  }

  public static TimePeriod from(JsonObject representation) {
    return new TimePeriod(representation);
  }
}
