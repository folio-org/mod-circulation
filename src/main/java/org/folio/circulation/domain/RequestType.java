package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.LoanAction.HOLDREQUESTED;
import static org.folio.circulation.domain.LoanAction.RECALLREQUESTED;

import java.util.Arrays;

public enum RequestType {
  NONE("", null),
  HOLD("Hold", HOLDREQUESTED),
  RECALL("Recall", RECALLREQUESTED),
  PAGE("Page", null);

  public final String value;
  public final LoanAction loanAction;

  public static RequestType from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  RequestType(String value, LoanAction loanAction) {
    this.value = value;
    this.loanAction = loanAction;
  }

  LoanAction toLoanAction() {
    return loanAction;
  }

  public String getValue() {
    return value;
  }

  public boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

    boolean isPage() {
      return equals(PAGE);
    }

  @Override
  public String toString() {
    return value;
  }
}
