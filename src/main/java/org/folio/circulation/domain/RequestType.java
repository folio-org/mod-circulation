package org.folio.circulation.domain;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;

public enum RequestType {
  NONE("", ItemStatus.NONE, null),
  HOLD("Hold", ItemStatus.CHECKED_OUT, "holdrequested"),
  RECALL("Recall", ItemStatus.CHECKED_OUT, "recallrequested"),
  PAGE("Page", ItemStatus.CHECKED_OUT, null);

  public final String value;
  public final ItemStatus checkedOutStatus;
  public final String loanAction;

  public static RequestType from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  RequestType(
    String value,
    ItemStatus checkedOutStatus,
    String loanAction) {

    this.value = value;
    this.checkedOutStatus = checkedOutStatus;
    this.loanAction = loanAction;
  }

  ItemStatus toCheckedOutItemStatus() {
    return checkedOutStatus;
  }

  String toLoanAction() {
    return loanAction;
  }

  public String getValue() {
    return value;
  }

  private boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
