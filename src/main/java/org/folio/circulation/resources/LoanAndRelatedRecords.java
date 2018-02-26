package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.InventoryRecords;

public class LoanAndRelatedRecords {
  public final JsonObject loan;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;
  public final String loanPolicyId;
  public final JsonObject location;

  LoanAndRelatedRecords(
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    JsonObject loan,
    JsonObject requestingUser,
    String loanPolicyId, JsonObject location) {

    this.loan = loan;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.loanPolicyId = loanPolicyId;
    this.location = location;
  }

  public LoanAndRelatedRecords replaceItem(JsonObject updatedItem) {
    return new LoanAndRelatedRecords(new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, loan, requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords changeLoan(JsonObject newLoan) {
    return new LoanAndRelatedRecords(inventoryRecords, requestQueue, newLoan,
      requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords changeUser(JsonObject newUser) {
    return new LoanAndRelatedRecords(inventoryRecords, requestQueue, loan, newUser,
      loanPolicyId, location);
  }

  public LoanAndRelatedRecords changeLoanPolicy(String newLoanPolicyId) {
    return new LoanAndRelatedRecords(inventoryRecords, requestQueue, loan,
      requestingUser, newLoanPolicyId, location);
  }

  public LoanAndRelatedRecords changeRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(inventoryRecords, newRequestQueue, loan,
      requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords changeLocation(JsonObject newLocation) {
    return new LoanAndRelatedRecords(inventoryRecords, requestQueue, loan,
      requestingUser, loanPolicyId, newLocation);
  }
}
