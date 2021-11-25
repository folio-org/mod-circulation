package org.folio.circulation.domain;

public enum RequestLevel {
  ITEM("Item"),
  TITLE("Title");

  private final String value;

  RequestLevel(String value) {
    this.value = value;
  }

  public String value() {
    return this.value;
  }

  @Override
  public String toString() {
    return this.value;
  }
}
