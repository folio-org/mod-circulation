package org.folio.circulation.domain.policy;

import java.util.HashMap;
import java.util.Map;

public enum OverdueFineInterval {
  MINUTE("minute", 1),
  HOUR("hour", 60),
  DAY("day", 1440),
  WEEK("week", 10080),
  MONTH("month", 44640),
  YEAR("year", 525600);

  private final String value;
  private final Integer minutes;

  private static final Map<String, OverdueFineInterval> CONSTANTS = new HashMap<>();

  static {
    for (OverdueFineInterval c: values()) {
      CONSTANTS.put(c.value, c);
    }
  }

  OverdueFineInterval(String value, Integer minutes) {
    this.value = value;
    this.minutes = minutes;
  }

  public Integer getMinutes() {
    return minutes;
  }

  public static OverdueFineInterval fromValue(String value) {
    OverdueFineInterval constant = CONSTANTS.get(value);
    if (constant == null) {
      throw new IllegalArgumentException(value);
    } else {
      return constant;
    }
  }
}
