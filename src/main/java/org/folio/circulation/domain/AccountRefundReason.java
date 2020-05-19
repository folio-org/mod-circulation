package org.folio.circulation.domain;

public enum AccountRefundReason {
  LOST_ITEM_FOUND("Lost item found");

  private final String value;

  AccountRefundReason(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
