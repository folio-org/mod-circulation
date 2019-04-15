package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.vertx.core.json.JsonObject;

class ConfigurationService {
  private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final DateTimeZone DEFAULT_DATE_TIME_ZONE = DateTimeZone.UTC;
  private static final String TIMEZONE_KEY = "timezone";
  private static final String RECORDS_NAME = "configs";

  DateTimeZone findDateTimeZone(JsonObject representation) {
    return from(representation, TimeZoneConfig::new, RECORDS_NAME)
      .map(MultipleRecords::getRecords)
      .map(this::findDateTimeZone)
      .orElse(DEFAULT_DATE_TIME_ZONE);
  }

  DateTimeZone findDateTimeZone(Collection<TimeZoneConfig> timeZoneConfigs) {
    final DateTimeZone chosenTimeZone = timeZoneConfigs.stream()
      .map(this::applyTimeZone)
      .findFirst()
      .orElse(DEFAULT_DATE_TIME_ZONE);

    log.info("Chosen timezone: `{}`", chosenTimeZone);

    return chosenTimeZone;
  }

  private DateTimeZone applyTimeZone(TimeZoneConfig item) {
    String value = item.getValue();
    return StringUtils.isBlank(value)
      ? DEFAULT_DATE_TIME_ZONE
      : parseDateTimeZone(value);
  }

  private DateTimeZone parseDateTimeZone(String value) {
    String timezone = new JsonObject(value).getString(TIMEZONE_KEY);
    return StringUtils.isBlank(timezone)
      ? DEFAULT_DATE_TIME_ZONE
      : DateTimeZone.forID(timezone);
  }
}
