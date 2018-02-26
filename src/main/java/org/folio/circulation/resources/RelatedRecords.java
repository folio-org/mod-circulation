package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.InventoryRecords;

public class RelatedRecords {
  public final JsonObject loan;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;

  RelatedRecords(
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    JsonObject loan) {

    this.loan = loan;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
  }

  public RelatedRecords replaceItem(JsonObject updatedItem) {
    return new RelatedRecords(new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, updatedItem);
  }

  public RelatedRecords replaceRequestQueue(RequestQueue newRequestQueue) {
    return new RelatedRecords(inventoryRecords, newRequestQueue, loan);
  }

  public RelatedRecords replaceLoan(JsonObject newLoan) {
    return new RelatedRecords(inventoryRecords, requestQueue, newLoan);
  }
}
