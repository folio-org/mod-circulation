package org.folio.circulation.domain.representations;

public enum AccountStatus {
  OPEN("Open"),
  CLOSED("Closed");

  private final String value;

  AccountStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
