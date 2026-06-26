package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.TimePeriod;

import io.vertx.core.json.JsonObject;

public class TimePeriodMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public TimePeriod toDomain(JsonObject representation) {
    log.debug("toDomain:: parameters intervalId: {}", () -> getProperty(representation, "intervalId"));
    return new TimePeriod(getIntegerProperty(representation, "duration", 0),
      getProperty(representation, "intervalId"));
  }
}
