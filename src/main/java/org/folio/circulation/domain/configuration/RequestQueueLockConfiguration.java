package org.folio.circulation.domain.configuration;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getIntegerProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class RequestQueueLockConfiguration {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());
  private static final int DEFAULT_TTL_MS = 10_000;
  private static final int DEFAULT_RETRY_INTERVAL_MS = 50;
  private static final int DEFAULT_MAX_WAIT_MS = 10_000;

  private final boolean requestQueueLockFeatureEnabled;
  private final int requestQueueLockTtlMs;
  private final int requestQueueLockRetryIntervalMs;
  private final int requestQueueLockMaxWaitMs;

  public static RequestQueueLockConfiguration from(JsonObject json) {
    try {
      return new RequestQueueLockConfiguration(
        json.getBoolean("requestQueueLockFeatureEnabled", true),
        positiveOrDefault(getIntegerProperty(json, "requestQueueLockTtlMs", DEFAULT_TTL_MS),
          DEFAULT_TTL_MS),
        positiveOrDefault(getIntegerProperty(json, "requestQueueLockRetryIntervalMs",
          DEFAULT_RETRY_INTERVAL_MS), DEFAULT_RETRY_INTERVAL_MS),
        positiveOrDefault(getIntegerProperty(json, "requestQueueLockMaxWaitMs",
          DEFAULT_MAX_WAIT_MS), DEFAULT_MAX_WAIT_MS));
    } catch (Exception exception) {
      log.error("Failed to parse request queue lock configuration, using defaults", exception);
      return new RequestQueueLockConfiguration(true, DEFAULT_TTL_MS,
        DEFAULT_RETRY_INTERVAL_MS, DEFAULT_MAX_WAIT_MS);
    }
  }

  private static int positiveOrDefault(Integer value, int defaultValue) {
    return value != null && value > 0 ? value : defaultValue;
  }
}
