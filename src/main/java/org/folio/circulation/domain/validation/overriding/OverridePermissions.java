package org.folio.circulation.domain.validation.overriding;

public enum OverridePermissions {

  OVERRIDE_PATRON_BLOCK("circulation.override-patron-block"),
  OVERRIDE_ITEM_LIMIT_BLOCK("circulation.override-item-limit-block"),
  OVERRIDE_ITEM_NOT_LOANABLE_BLOCK("circulation.override-item-not-loanable-block");

  private final String value;

  OverridePermissions(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
