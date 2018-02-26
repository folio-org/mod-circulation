package org.folio.circulation.resources;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.support.InventoryRecords;

public class RelatedRecords {
  public final JsonObject loan;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;
  public final String loanPolicyId;

  RelatedRecords(
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    JsonObject loan,
    JsonObject requestingUser,
    String loanPolicyId) {

    this.loan = loan;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.loanPolicyId = loanPolicyId;
  }

  public RelatedRecords replaceItem(JsonObject updatedItem) {
    return new RelatedRecords(new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, updatedItem, requestingUser, loanPolicyId);
  }

  public RelatedRecords replaceLoan(JsonObject newLoan) {
    return new RelatedRecords(inventoryRecords, requestQueue, newLoan,
      requestingUser, loanPolicyId);
  }

  public RelatedRecords changeUser(JsonObject newUser) {
    return new RelatedRecords(inventoryRecords, requestQueue, loan, newUser,
      loanPolicyId);
  }

  public RelatedRecords changeLoanPolicy(String newLoanPolicyId) {
    return new RelatedRecords(inventoryRecords, requestQueue, loan,
      requestingUser, newLoanPolicyId);
  }
}
