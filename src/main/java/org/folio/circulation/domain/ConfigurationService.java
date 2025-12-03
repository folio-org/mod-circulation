package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class ConfigurationService {
  private final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private static final int DEFAULT_SCHEDULED_NOTICES_PROCESSING_LIMIT = 100;
  private static final int DEFAULT_CHECKOUT_TIMEOUT_DURATION_IN_MINUTES = 3;
  private static final String CHECKOUT_TIMEOUT_DURATION_KEY = "checkoutTimeoutDuration";
  private static final String CHECKOUT_TIMEOUT_KEY = "checkoutTimeout";

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

}
