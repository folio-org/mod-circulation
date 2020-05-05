package org.folio.circulation.domain.representations;

public enum FeeFinePaymentAction {
  CANCELLED_ITEM_RETURNED("Cancelled item returned");

  private String value;
  FeeFinePaymentAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
