package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class ItemStatus {
  private ItemStatus() { }

  public static final String AVAILABLE = "Available";
  public static final String CHECKED_OUT = "Checked out";
  public static final String CHECKED_OUT_HELD = "Checked out - Held";
  public static final String CHECKED_OUT_RECALLED = "Checked out - Recalled";
  public static final String AWAITING_PICKUP = "Awaiting pickup";

  public static boolean isCheckedOut(JsonObject item) {
    return isCheckedOut(getStatus(item));
  }

  public static boolean isCheckedOut(String status) {
    return status.equals(CHECKED_OUT)
      || status.equals(CHECKED_OUT_HELD)
      || status.equals(CHECKED_OUT_RECALLED);
  }

  public static String getStatus(JsonObject item) {
    return item.getJsonObject("status").getString("name");
  }
}
