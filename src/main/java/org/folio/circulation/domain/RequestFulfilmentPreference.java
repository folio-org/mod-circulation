package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.domain.ItemStatus.AVAILABLE;
import static org.folio.circulation.domain.ItemStatus.AWAITING_PICKUP;

public class RequestFulfilmentPreference {
  public static final String HOLD_SHELF = "Hold Shelf";
  public static final String DELIVERY = "Delivery";

  public final String value;

  private RequestFulfilmentPreference(String value) {
    this.value = value;
  }

  public static RequestFulfilmentPreference from(JsonObject request) {
    return new RequestFulfilmentPreference(request.getString("fulfilmentPreference"));
  }

  public String toCheckedInItemStatus() {
    switch(value) {
      case RequestFulfilmentPreference.HOLD_SHELF:
        return AWAITING_PICKUP;

      case RequestFulfilmentPreference.DELIVERY:
        return AVAILABLE;

      default:
        //TODO: Need to add validation to stop this situation
        return "";
    }
  }
}
