package org.folio.circulation.domain.notes;

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
