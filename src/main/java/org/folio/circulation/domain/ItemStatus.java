package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum ItemStatus {
  NONE(""),
  AVAILABLE("Available"),
  AWAITING_PICKUP("Awaiting pickup"),
  CHECKED_OUT("Checked out"),
  IN_TRANSIT("In transit"),
  MISSING("Missing"),
  PAGED("Paged"),
  ON_ORDER("On order"),
  IN_PROCESS("In process");

  public static ItemStatus from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  private final String value;

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

  public void setDate(String date) {
    this.date = date;
  }

  private boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
