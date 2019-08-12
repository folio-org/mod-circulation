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

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final DateTimeZone DEFAULT_DATE_TIME_ZONE = DateTimeZone.UTC;
  private static final String TIMEZONE_KEY = "timezone";
  private static final String RECORDS_NAME = "configs";

  DateTimeZone findDateTimeZone(JsonObject representation) {
    return from(representation, Configuration::new, RECORDS_NAME)
      .map(MultipleRecords::getRecords)
      .map(this::findDateTimeZone)
      .orElse(DEFAULT_DATE_TIME_ZONE);
  }

  DateTimeZone findDateTimeZone(Collection<Configuration> configurations) {
    final DateTimeZone chosenTimeZone = configurations.stream()
      .map(this::applyTimeZone)
      .findFirst()
      .orElse(DEFAULT_DATE_TIME_ZONE);

    log.info("Chosen timezone: `{}`", chosenTimeZone);

    return chosenTimeZone;
  }

  Integer findSchedulerNoticesLimit(Collection<Configuration> configurations) {
    final Integer noticesLimit = configurations.stream()
      .map(this::applySchedulerNoticesLimit)
      .findFirst()
      .orElse(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT);

    log.info("Scheduled notices processing limit: `{}`", noticesLimit);

    return noticesLimit;
  }

  private Integer applySchedulerNoticesLimit(Configuration config) {
    String value = config.getValue();
    return StringUtils.isNumeric(value)
      ? Integer.valueOf(value)
      : DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT;
  }

  private DateTimeZone applyTimeZone(Configuration config) {
    String value = config.getValue();
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
