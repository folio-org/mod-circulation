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

  boolean canCreateRequestForItem(Item item) {
    String status = item.getStatus();

    switch (value) {
      case HOLD:
      case RECALL:
        return StringUtils.equalsIgnoreCase(status, CHECKED_OUT);

      case PAGE:
      default:
        return true;
    }
  }

  String toCheckedOutItemStatus() {
    switch(value) {
      case RequestType.HOLD:
      case RequestType.RECALL:
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
