package org.folio.circulation.domain;

import static org.folio.circulation.domain.MultipleRecords.from;

import java.lang.invoke.MethodHandles;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class ConfigurationService {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final int DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES = 3;
  private static final ZoneId DEFAULT_DATE_TIME_ZONE = ZoneOffset.UTC;
  private static final String TIMEZONE_KEY = "timezone";
  private static final String RECORDS_NAME = "configs";
  private static final String CHECKOUT_TIMEOUT_DURATION_KEY = "checkoutTimeoutDuration";
  private static final String CHECKOUT_TIMEOUT_KEY = "checkoutTimeout";

  ZoneId findDateTimeZone(JsonObject representation) {
    return from(representation, Configuration::new, RECORDS_NAME)
      .map(MultipleRecords::getRecords)
      .map(this::findDateTimeZone)
      .orElse(DEFAULT_DATE_TIME_ZONE);
  }

  public ZoneId findDateTimeZone(Collection<Configuration> configurations) {
    final ZoneId chosenTimeZone = configurations.stream()
      .map(this::applyTimeZone)
      .findFirst()
      .orElse(DEFAULT_DATE_TIME_ZONE);

    log.debug("findDateTimeZone:: timezone={}", chosenTimeZone);

    return chosenTimeZone;
  }

  public Integer findSchedulerNoticesLimit(Collection<Configuration> configurations) {
    final Integer noticesLimit = configurations.stream()
      .map(this::applySchedulerNoticesLimit)
      .findFirst()
      .orElse(DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT);

    log.debug("findSchedulerNoticesLimit:: limit={}", noticesLimit);

    return noticesLimit;
  }

  public Integer findSessionTimeout(Collection<Configuration> configurations) {
    final Integer sessionTimeout = configurations.stream()
      .map(this::applySessionTimeout)
      .findFirst()
      .orElse(DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES);

    log.debug("findSessionTimeout:: timeout={}", sessionTimeout);

    return sessionTimeout;
  }

  private Integer applySessionTimeout(Configuration config) {
    String value = config.getValue();
    JsonObject otherSettingsConfigJson = new JsonObject(value);

    return isConfigurationEmptyOrUnavailable(otherSettingsConfigJson)
      ? DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES
      : findTimeoutDuration(otherSettingsConfigJson);
  }

  private boolean isConfigurationEmptyOrUnavailable(JsonObject configJson) {
    return configJson.isEmpty() || !configJson.getBoolean(CHECKOUT_TIMEOUT_KEY, false);
  }

  private Integer findTimeoutDuration(JsonObject configJson) {
    try {
      return Integer.parseInt(configJson
        .getValue(CHECKOUT_TIMEOUT_DURATION_KEY, DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES).toString());
    } catch (NumberFormatException e) {
      log.warn("findTimeoutDuration:: can't parse property {} {}. Using default value: {}",
        CHECKOUT_TIMEOUT_DURATION_KEY, e.getMessage(), DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES);
      return DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES;
    }
  }

  private Integer applySchedulerNoticesLimit(Configuration config) {
    String value = config.getValue();
    return StringUtils.isNumeric(value)
      ? Integer.valueOf(value)
      : DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT;
  }

  private ZoneId applyTimeZone(Configuration config) {
    String value = config.getValue();
    return StringUtils.isBlank(value)
      ? DEFAULT_DATE_TIME_ZONE
      : parseDateTimeZone(value);
  }

  private ZoneId parseDateTimeZone(String value) {
    String timezone = new JsonObject(value).getString(TIMEZONE_KEY);
    return StringUtils.isBlank(timezone)
      ? DEFAULT_DATE_TIME_ZONE
      : ZoneId.of(timezone);
  }
}
