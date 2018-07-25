package org.folio.circulation.domain;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public enum ItemStatus {
  NONE(""),
  AVAILABLE("Available"),
  AWAITING_PICKUP("Awaiting pickup"),
  CHECKED_OUT("Checked out");

  public static ItemStatus from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  private final String value;

  ItemStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  private boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
