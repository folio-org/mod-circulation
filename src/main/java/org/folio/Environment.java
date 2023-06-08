package org.folio;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Environment {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Environment() { }

  public static int getScheduledAnonymizationNumberOfLoansToCheck() {
    return getVariable("SCHEDULED_ANONYMIZATION_NUMBER_OF_LOANS_TO_CHECK", 50000);
  }

  public static boolean getCheckOutFeatureFlag() {
    return Boolean.parseBoolean(getPropertyValue("CHECKOUT_LOCK_FEATURE_ENABLED", "false"));
  }

  public static List<Integer> getRetryIntervals() {
    return getIntervalArr(getPropertyValue("RETRY_INTERVAL_MS","500|500|1000"));
  }

  public static int getLockTTL() {
    return Integer.parseInt(getPropertyValue("LOCK_TTL_MS", "2000"));
  }

  private static int getVariable(String key, int defaultValue) {
    final var variable = System.getenv().get(key);

    if (isBlank(variable)) {
      return defaultValue;
    }

    try {
      return parseInt(variable);
    }
    catch(Exception e) {
      log.warn("Invalid value for '{}': '{}' ", key, variable);

      return defaultValue;
    }
  }

  private static String getPropertyValue(String key, String defaultValue) {
    return Optional.ofNullable(System.getenv().get(key))
      .or(() -> Optional.ofNullable(System.getProperty(key)))
      .orElse(defaultValue);
  }

  private static List<Integer> getIntervalArr(String intervals) {
      return Arrays.stream(intervals.split("\\|"))
        .map(Integer::parseInt)
        .collect(Collectors.toList());
  }
}
