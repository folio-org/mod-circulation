package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.folio.circulation.domain.ItemStatus.AWAITING_DELIVERY;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum RequestFulfillmentPreference {
  NONE(""),
  HOLD_SHELF("Hold Shelf"),
  DELIVERY("Delivery");

  private final String value;

  private static final List<RequestFulfillmentPreference> ALLOWED_FULFILLMENT_PREFERENCES =
    Arrays.stream(values())
      .filter(fulfillmentPreference -> fulfillmentPreference != NONE)
      .collect(Collectors.toList());

  private static final List<String> ALLOWED_VALUES = ALLOWED_FULFILLMENT_PREFERENCES.stream()
    .map(RequestFulfillmentPreference::getValue)
    .collect(Collectors.toList());

  RequestFulfillmentPreference(String value) {
    this.value = value;
  }

  public static RequestFulfillmentPreference from(String value) {
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
        return AWAITING_DELIVERY;

      default:
        return ItemStatus.NONE;
    }
  }

  public String getValue() {
    return value;
  }

  private boolean nameMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

  public static List<RequestFulfillmentPreference> allowedFulfillmentPreferences() {
    return ALLOWED_FULFILLMENT_PREFERENCES;
  }

  public static List<String> allowedValues() {
    return ALLOWED_VALUES;
  }
}
