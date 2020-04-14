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
  MISSING("markedMissing");

  private final String value;

  LoanAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public enum ResolveClaimedReturned {
    CHECKED_IN_RETURNED_BY_PATRON("checkedInReturnedByPatron"),
    CHECKED_IN_FOUND_BY_LIBRARY("checkedInFoundByLibrary");

    private final String value;

    ResolveClaimedReturned(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
