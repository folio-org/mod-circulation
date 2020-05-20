package org.folio.circulation.domain.representations;

public enum AccountPaymentStatus {
  CANCELLED_ITEM_RETURNED("Cancelled item returned"),
  REFUNDED_FULLY("Refunded fully"),
  CREDITED_FULLY("Credited fully");

  private final String value;
  AccountPaymentStatus(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
