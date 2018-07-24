package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;
import static org.folio.circulation.domain.ItemStatus.NONE;

public class RequestFulfilmentPreference {
  static final String HOLD_SHELF = "Hold Shelf";
  private static final String DELIVERY = "Delivery";

  public final String value;

  private RequestFulfilmentPreference(String value) {
    this.value = value;
  }

  public static RequestFulfilmentPreference from(Request request) {
    return new RequestFulfilmentPreference(request.getFulfilmentPreference());
  }

  ItemStatus toCheckedInItemStatus() {
    switch(value) {
      case RequestFulfilmentPreference.HOLD_SHELF:
        return AWAITING_PICKUP;

      case RequestFulfilmentPreference.DELIVERY:
        return AVAILABLE;

      default:
        //TODO: Need to add validation to stop this situation
        return NONE;
    }
  }
}
