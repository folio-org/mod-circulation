package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum ItemStatusName {
  NONE(""),
  AVAILABLE("Available"),
  AWAITING_PICKUP("Awaiting pickup"),
  AWAITING_DELIVERY("Awaiting delivery"),
  CHECKED_OUT("Checked out"),
  IN_TRANSIT("In transit"),
  MISSING("Missing"),
  PAGED("Paged"),
  ON_ORDER("On order"),
  IN_PROCESS("In process"),
  DECLARED_LOST("Declared lost"),
  CLAIMED_RETURNED("Claimed returned"),
  WITHDRAWN("Withdrawn"),
  LOST_AND_PAID("Lost and paid"),
  INTELLECTUAL_ITEM("Intellectual item"),
  IN_PROCESS_NON_REQUESTABLE("In process (non-requestable)"),
  LONG_MISSING("Long missing"),
  UNAVAILABLE("Unavailable"),
  UNKNOWN("Unknown"),
  RESTRICTED("Restricted"),
  AGED_TO_LOST("Aged to lost");

  public static ItemStatusName from(String value) {
    return Arrays.stream(values())
      .filter(status -> equalsIgnoreCase(status.name, value))
      .findFirst()
      .orElse(NONE);
  }

  private final String name;

  ItemStatusName(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public boolean isLostNotResolved() {
    return this == DECLARED_LOST || this == AGED_TO_LOST;
  }
}
