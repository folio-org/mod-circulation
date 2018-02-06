package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT_HELD;
import static org.folio.circulation.domain.ItemStatus.CHECKED_OUT_RECALLED;

public class RequestType {
  private static final String RECALL = "Recall";
  private static final String HOLD = "Hold";
  private static final String PAGE = "Page";

  public final String value;

  public static RequestType from(JsonObject request) {
    return new RequestType(request.getString("requestType"));
  }

  private RequestType(String value) {
    this.value = value;
  }

  public boolean canCreateRequestForItem(JsonObject item) {
    String status = item.getJsonObject("status").getString("name");

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

  public String toItemStatus() {
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

  public String toloanAction() {
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
