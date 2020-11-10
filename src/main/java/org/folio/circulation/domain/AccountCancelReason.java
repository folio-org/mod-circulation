package org.folio.circulation.domain;

public enum AccountCancelReason {
  CANCELLED_ITEM_RETURNED("Cancelled item returned"),
  CANCELLED_ITEM_RENEWED("Cancelled item renewed");

  private final String value;
  AccountCancelReason(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
