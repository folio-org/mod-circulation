package org.folio.circulation.domain;

public enum AccountCancelReason {
  CANCELLED_ITEM_RETURNED("Cancelled item returned"),
  CANCELLED_ITEM_RENEWED("Cancelled item renewed"),
  CANCELLED_ITEM_DECLARED_LOST("Cancelled item declared lost");

  private final String value;
  AccountCancelReason(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
