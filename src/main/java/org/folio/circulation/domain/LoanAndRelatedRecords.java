package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class LoanAndRelatedRecords {
  public final JsonObject loan;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;
  public final String loanPolicyId;
  public final JsonObject location;

  private LoanAndRelatedRecords(
    JsonObject loan, InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    JsonObject requestingUser,
    String loanPolicyId, JsonObject location) {

    this.loan = loan;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.loanPolicyId = loanPolicyId;
    this.location = location;
  }

  public LoanAndRelatedRecords(JsonObject loan) {
    this(loan, null, null, null, null, null);
  }

  public LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return new LoanAndRelatedRecords(loan, new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords withLoan(JsonObject newLoan) {
    return new LoanAndRelatedRecords(newLoan, inventoryRecords, requestQueue,
      requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords withRequestingUser(JsonObject newUser) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      newUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords withLoanPolicy(String newLoanPolicyId) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, newLoanPolicyId, location);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, newRequestQueue,
      requestingUser, loanPolicyId, location);
  }

  public LoanAndRelatedRecords withLocation(JsonObject newLocation) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, loanPolicyId, newLocation);
  }

  public LoanAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    return new LoanAndRelatedRecords(loan, newInventoryRecords, requestQueue,
      requestingUser, loanPolicyId, location);
  }
}
