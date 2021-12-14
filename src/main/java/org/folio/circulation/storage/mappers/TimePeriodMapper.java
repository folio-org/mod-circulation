package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.TimePeriod;

import io.vertx.core.json.JsonObject;

public class TimePeriodMapper {
  public TimePeriod toDomain(JsonObject representation) {
    return new TimePeriod(getIntegerProperty(representation, "duration", 0),
      getProperty(representation, "intervalId"));
  }
}
