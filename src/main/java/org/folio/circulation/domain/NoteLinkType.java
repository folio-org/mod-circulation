package org.folio.circulation.domain;

public enum NoteLinkType {
  USER("user");

  private final String value;

  NoteLinkType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
