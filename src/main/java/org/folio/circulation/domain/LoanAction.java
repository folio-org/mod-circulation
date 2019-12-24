package org.folio.circulation.domain;

public enum LoanAction {
  DECLARED_LOST("declaredLost"),
  RENEWED("renewed"),
  RENEWED_THROUGH_OVERRIDE("renewedThroughOverride"),
  CHECKED_IN("checkedin"),
  CHECKED_OUT("checkedout"),
  CHECKED_OUT_THROUGH_OVERRIDE("checkedOutThroughOverride"),
  RECALLREQUESTED("recallrequested"),
  HOLDREQUESTED("holdrequested"),
  DUE_DATE_CHANGE("dueDateChange");

  private final String value;

  LoanAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
