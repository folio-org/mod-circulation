package org.folio.circulation.domain;

import java.util.Arrays;

public enum ItemStatus {
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

  private final ItemStatusName name;

  public static ItemStatus from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  public static ItemStatus from(String value, String date) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst().map(status -> {
        status.setDate(date);
        return status;
      })
      .orElse(NONE);
  }

  // FIXME: Enum constants are singletons, date must not be associated with it
  private String date;

  ItemStatus(String value) {
    name = ItemStatusName.from(value);
  }

  public String getValue() {
    return name.getName();
  }

  public ItemStatusName getName() {
    return name;
  }

  public String getDate() {
    return date;
  }

  void setDate(String date) {
    this.date = date;
  }

  private boolean valueMatches(String value) {
    return name.equals(ItemStatusName.from(value));
  }

  public boolean is(ItemStatusName name) {
    return this.name.equals(name);
  }

  public boolean isLostNotResolved() {
    return getName().isLostNotResolved();
  }
}
