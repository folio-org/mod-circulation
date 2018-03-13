package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class RequestAndRelatedRecords {
  public final JsonObject request;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;

  private RequestAndRelatedRecords(
    JsonObject request,
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue, JsonObject requestingUser) {

    this.request = request;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
  }

  public RequestAndRelatedRecords(JsonObject request) {
    this(request, null, null, null);
  }

  public RequestAndRelatedRecords withItem(JsonObject updatedItem) {
    return new RequestAndRelatedRecords(request, new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, requestingUser);
  }

  public RequestAndRelatedRecords withRequest(JsonObject newRequest) {
    return new RequestAndRelatedRecords(newRequest, inventoryRecords,
      requestQueue, requestingUser);
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(request, inventoryRecords,
      newRequestQueue, requestingUser);
  }

  public RequestAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    return new RequestAndRelatedRecords(request, newInventoryRecords,
      requestQueue, requestingUser);
  }

  public RequestAndRelatedRecords withRequestingUser(JsonObject newUser) {
    return new RequestAndRelatedRecords(request, inventoryRecords, requestQueue,
      newUser);
  }
}
