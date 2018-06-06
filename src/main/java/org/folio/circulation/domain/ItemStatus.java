package org.folio.circulation.domain;

public class ItemStatus {
  private ItemStatus() { }

  static final String AVAILABLE = "Available";
  static final String AWAITING_PICKUP = "Awaiting pickup";
  public static final String CHECKED_OUT = "Checked out";
  public static final String CHECKED_OUT_HELD = "Checked out - Held";
  public static final String CHECKED_OUT_RECALLED = "Checked out - Recalled";
}
