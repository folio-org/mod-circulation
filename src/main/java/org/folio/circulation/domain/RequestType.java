package org.folio.circulation.domain;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;

public enum RequestType {
  NONE("", ItemStatus.NONE, null),
  HOLD("Hold", ItemStatus.CHECKED_OUT, "holdrequested"),
  RECALL("Recall", ItemStatus.CHECKED_OUT, "recallrequested"),
  PAGE("Page", ItemStatus.CHECKED_OUT, null);

  public final String name;
  public final ItemStatus checkedOutStatus;
  public final String loanAction;

  public static RequestType from(Request request) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(request.getRequestType()))
      .findFirst()
      .orElse(NONE);
  }

  RequestType(String name, ItemStatus checkedOutStatus, String loanAction) {
    this.name = name;
    this.checkedOutStatus = checkedOutStatus;
    this.loanAction = loanAction;
  }

  boolean canCreateRequestForItem(Item item) {
    switch (this) {
      case HOLD:
      case RECALL:
        return item.getStatus().equals(CHECKED_OUT);

      case PAGE:
      default:
        return true;
    }
  }

  ItemStatus toCheckedOutItemStatus() {
    return checkedOutStatus;
  }

  String toLoanAction() {
    return loanAction;
  }

  public String getName() {
    return name;
  }

  private boolean nameMatches(String name) {
    return equalsIgnoreCase(getName(), name);
  }
}
