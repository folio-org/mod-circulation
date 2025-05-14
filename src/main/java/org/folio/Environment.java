package org.folio;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Environment {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Environment() { }

  public static int getScheduledAnonymizationNumberOfLoansToCheck() {
    return getVariable("SCHEDULED_ANONYMIZATION_NUMBER_OF_LOANS_TO_CHECK", 50000);
  }

  public static boolean getEnableFloatingCollections() {
    return getVariable("ENABLE_FLOATING_COLLECTIONS", false);
  }

  public static int getHttpMaxPoolSize() {
    return getVariable("HTTP_MAXPOOLSIZE", 100);
  }

  public static boolean getEcsTlrFeatureEnabled() {
    return getVariable("ECS_TLR_FEATURE_ENABLED", false);
  }

  private static int getVariable(String key, int defaultValue) {
    final var variable = getVar(key);

    if (isBlank(variable)) {
      log.info("getVariable:: environment variable '{}' is not set, using default value: '{}'",
        key, defaultValue);
      return defaultValue;
    }

    try {
      int parsedIntVariable = parseInt(variable);
      log.info("getVariable:: environment variable: '{}' parsed value: '{}'",
        key, parsedIntVariable);
      return parsedIntVariable;
    }
    catch(Exception e) {
      log.warn("Invalid value for '{}': '{}', using default value: '{}'",
        key, variable, defaultValue);

      return defaultValue;
    }
  }

  private static boolean getVariable(String key, Boolean defaultValue) {
      return parseBoolean(Objects.toString(getVar(key),defaultValue.toString()));
  }

  private static String getVar(String key) {
    return MOCK_ENV.containsKey(key) ? MOCK_ENV.get(key) : System.getenv().get(key);
  }
  // Mock environment variables for unit testing.
  public static final Map<String,String> MOCK_ENV = new HashMap<>();
}
