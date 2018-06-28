package org.folio.circulation.domain;

import org.apache.commons.lang3.StringUtils;

import static org.folio.circulation.domain.ItemStatus.*;

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

  public boolean canCreateRequestForItem(Item item) {
    String status = item.getStatus();

    switch (value) {
      case HOLD:
      case RECALL:
        return StringUtils.equalsIgnoreCase(status, CHECKED_OUT) ||
          StringUtils.equalsIgnoreCase(status, CHECKED_OUT_HELD) ||
            StringUtils.equalsIgnoreCase(status, CHECKED_OUT_RECALLED);

      case PAGE:
      default:
        return true;
    }
  }

  String toCheckedOutItemStatus() {
    switch(value) {
      case RequestType.HOLD:
        return CHECKED_OUT_HELD;

      case RequestType.RECALL:
        return CHECKED_OUT_RECALLED;

      case RequestType.PAGE:
        return CHECKED_OUT;

      default:
        //TODO: Need to add validation to stop this situation
        return "";
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
