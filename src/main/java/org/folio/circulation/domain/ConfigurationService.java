package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTimeZone;

import java.util.List;

class ConfigurationService {

  private static final DateTimeZone DEFAULT_DATE_TIME_ZONE = DateTimeZone.UTC;
  private static final String TIMEZONE_KEY = "timezone";

  DateTimeZone findDateTimeZone(JsonObject representation) {
    AdjacentConfigurations configurations = new AdjacentConfigurations(representation);
    int totalRecords = configurations.getTotalRecords();
    if (totalRecords <= 0) {
      return DEFAULT_DATE_TIME_ZONE;
    }

    List<TimeZoneConfig> timeZoneConfigs = configurations.getTimeZoneConfigs();
    return timeZoneConfigs.stream()
      .map(this::applyTimeZone)
      .findFirst()
      .orElse(DEFAULT_DATE_TIME_ZONE);
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
