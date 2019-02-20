package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.time.temporal.ChronoUnit;

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

  public static TimePeriod from(JsonObject representation) {
    return new TimePeriod(representation);
  }
}
