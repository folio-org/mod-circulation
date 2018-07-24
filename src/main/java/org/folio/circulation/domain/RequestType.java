package org.folio.circulation.domain;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.NONE;

public class RequestType {
  private static final String RECALL = "Recall";
  private static final String HOLD = "Hold";
  private static final String PAGE = "Page";

  public final String value;

  public static RequestType from(Request request) {
    return new RequestType(request.getRequestType());
  }

  private RequestType(String value) {
    this.value = value;
  }

  boolean canCreateRequestForItem(Item item) {
    switch (value) {
      case HOLD:
      case RECALL:
        return item.getStatus().equals(CHECKED_OUT);

      case PAGE:
      default:
        return true;
    }
  }

  ItemStatus toCheckedOutItemStatus() {
    switch(value) {
      case RequestType.HOLD:
      case RequestType.RECALL:
      case RequestType.PAGE:
        return CHECKED_OUT;

      default:
        //TODO: Need to add validation to stop this situation
        return NONE;
    }
  }

  String toLoanAction() {
    switch (this.value) {
      case HOLD:
        return "holdrequested";
      case RECALL:
        return "recallrequested";

      case PAGE:
      default:
        return null;
    }
  }
}
