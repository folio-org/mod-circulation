package org.folio.circulation.resources;

import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.InventoryRecords;

public class RelatedRecords {
  private final InventoryRecords inventoryRecords;
  private final RequestQueue requestQueue;

  public RelatedRecords(InventoryRecords inventoryRecords, RequestQueue requestQueue) {

    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
  }

  public InventoryRecords inventoryRecords() {
    return inventoryRecords;
  }

  public RequestQueue requestQueue() {
    return requestQueue;
  }
}
