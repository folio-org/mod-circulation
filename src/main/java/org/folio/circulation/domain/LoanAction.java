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
  CLAIMED_RETURNED("claimedReturned"),
  MISSING("markedMissing"),
  CLOSED_LOAN("closedLoan"),

  //Resolve claimed return actions:
  CLAIM_RETURNED_BY_PATRON("checkedInReturnedByPatron"),
  CLAIM_FOUND_BY_LIBRARY("checkedInFoundByLibrary");


  private final String value;

  LoanAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
