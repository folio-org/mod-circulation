package org.folio.circulation.domain;

import java.util.Arrays;

public enum LoanStatus {
  OPEN("Open"),
  CLOSED("Closed");

  private final String value;

  LoanStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static LoanStatus fromValue(String value) {
    return Arrays.stream(values())
      .filter(enumElement -> enumElement.getValue().equals(value))
      .findFirst()
      .orElse(null);
  }
}
