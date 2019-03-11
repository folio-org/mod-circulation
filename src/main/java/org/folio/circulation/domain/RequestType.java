package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;

public enum RequestType {
  NONE("", null),
  HOLD("Hold", "holdrequested"),
  RECALL("Recall", "recallrequested"),
  PAGE("Page", null);

  public final String value;
  public final String loanAction;

  public static RequestType from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  RequestType(String value, String loanAction) {
    this.value = value;
    this.loanAction = loanAction;
  }

  String toLoanAction() {
    return loanAction;
  }

  public String getValue() {
    return value;
  }

  public boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
