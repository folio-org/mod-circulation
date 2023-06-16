package org.folio;

import static java.lang.Integer.parseInt;
import static org.apache.commons.lang.StringUtils.isBlank;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Environment {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private Environment() { }

  public static int getScheduledAnonymizationNumberOfLoansToCheck() {
    return getVariable("SCHEDULED_ANONYMIZATION_NUMBER_OF_LOANS_TO_CHECK", 50000);
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
}
