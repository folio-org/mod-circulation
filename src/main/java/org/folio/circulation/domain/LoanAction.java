package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum LoanAction {
  NONE(""),
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
  ITEM_AGED_TO_LOST("itemAgedToLost"),
  DUE_DATE_CHANGED("dueDateChanged"),
  PATRON_INFO_ADDED("patronInfoAdded"),
  STAFF_INFO_ADDED("staffInfoAdded"),
  RESOLVE_CLAIM_AS_RETURNED_BY_PATRON("checkedInReturnedByPatron"),
  RESOLVE_CLAIM_AS_FOUND_BY_LIBRARY("checkedInFoundByLibrary"),

  REMINDER_FEE("reminderFee");

  private final String value;

  LoanAction(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static LoanAction from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  private boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
