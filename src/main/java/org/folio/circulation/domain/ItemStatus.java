package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

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
  AGED_TO_LOST("Aged to lost");

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

  private final String value;
  // FIXME: Enum constants are singletons, date must not be associated with it
  private String date;

  ItemStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public String getDate() {
    return date;
  }

  void setDate(String date) {
    this.date = date;
  }

  private boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

  public boolean isLostNotResolved() {
    return this == DECLARED_LOST || this == AGED_TO_LOST;
  }
}
