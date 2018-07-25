package org.folio.circulation.domain;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;

public enum RequestFulfilmentPreference {
  NONE(""),
  HOLD_SHELF("Hold Shelf"),
  DELIVERY("Delivery");

  private final String value;

  RequestFulfilmentPreference(String value) {
    this.value = value;
  }

  public static RequestFulfilmentPreference from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  ItemStatus toCheckedInItemStatus() {
    switch(this) {
      case HOLD_SHELF:
        return AWAITING_PICKUP;

      case DELIVERY:
        return AVAILABLE;

      default:
        //TODO: Need to add validation to stop this situation
        return ItemStatus.NONE;
    }
  }

  public String getValue() {
    return value;
  }

  private boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }
}
