package org.folio;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
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
    return getVariable("CHECKOUT_LOCK_FEATURE_ENABLED", false);
  }

  public static List<Integer> getRetryInterval() {
    return getIntervalArr("RETRY_INTERVAL_MS",300);
  }

  public static int getLockTTL() {
    return getVariable("LOCK_TTL_MS", 3000);
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

  private static Boolean getVariable(String key, boolean defaultValue) {
    final var variable = System.getenv().get(key);

    if (isBlank(variable)) {
      return defaultValue;
    }

    try {
      return Boolean.parseBoolean(variable);
    }
    catch(Exception e) {
      log.warn("Invalid value for '{}': '{}' ", key, variable);

      return defaultValue;
    }
  }

  private static List<Integer> getIntervalArr(String key, int defaultValue) {
    final var variable = System.getenv().get(key);

    if (isBlank(variable)) {
      return List.of(defaultValue);
    }

    try {
      return Arrays.stream(variable.split("|"))
        .map(Integer::parseInt)
        .collect(Collectors.toList());
    } catch (Exception e) {
      log.warn("Invalid value for '{}': '{}' ", key, variable);

      return List.of(defaultValue);
    }
  }
}
