package org.folio.circulation.domain;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public enum ItemStatus {
  NONE(""),
  AVAILABLE("Available"),
  AWAITING_PICKUP("Awaiting pickup"),
  CHECKED_OUT("Checked out");

  public static ItemStatus from(String name) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(name))
      .findFirst()
      .orElse(NONE);
  }

  private final String name;

  ItemStatus(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  private boolean nameMatches(String name) {
    return equalsIgnoreCase(getName(), name);
  }
}
