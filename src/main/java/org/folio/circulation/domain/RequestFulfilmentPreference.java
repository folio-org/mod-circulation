package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.ItemStatusName.AWAITING_DELIVERY;
import static org.folio.circulation.domain.ItemStatusName.AWAITING_PICKUP;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum RequestFulfilmentPreference {
  NONE(""),
  HOLD_SHELF("Hold Shelf"),
  DELIVERY("Delivery");

  private final String value;

  private static final List<RequestFulfilmentPreference> ALLOWED_FULFILMENT_PREFERENCES =
    Arrays.stream(values())
      .filter(fulfilmentPreference -> fulfilmentPreference != NONE)
      .collect(Collectors.toList());

  private static final List<String> ALLOWED_VALUES = ALLOWED_FULFILMENT_PREFERENCES.stream()
    .map(RequestFulfilmentPreference::getValue)
    .collect(Collectors.toList());

  RequestFulfilmentPreference(String value) {
    this.value = value;
  }

  public static RequestFulfilmentPreference from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  ItemStatusName toCheckedInItemStatus() {
    switch(this) {
      case HOLD_SHELF:
        return AWAITING_PICKUP;

      case DELIVERY:
        return AWAITING_DELIVERY;

      default:
        return ItemStatusName.NONE;
    }
  }

  public String getValue() {
    return value;
  }

  private boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

  public static List<RequestFulfilmentPreference> allowedFulfilmentPreferences() {
    return ALLOWED_FULFILMENT_PREFERENCES;
  }

  public static List<String> allowedValues() {
    return ALLOWED_VALUES;
  }
}
