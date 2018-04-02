package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class LoanAndRelatedRecords {
  public final JsonObject loan;
  public final InventoryRecords inventoryRecords;
  public final RequestQueue requestQueue;
  public final JsonObject requestingUser;
  public final JsonObject proxyingUser;
  public final JsonObject loanPolicy;
  public final JsonObject location;
  public final JsonObject materialType;

  private LoanAndRelatedRecords(
    JsonObject loan,
    InventoryRecords inventoryRecords,
    RequestQueue requestQueue,
    JsonObject requestingUser,
    JsonObject proxyingUser,
    JsonObject loanPolicy,
    JsonObject location,
    JsonObject materialType) {

    this.loan = loan;
    this.inventoryRecords = inventoryRecords;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.proxyingUser = proxyingUser;
    this.loanPolicy = loanPolicy;
    this.location = location;
    this.materialType = materialType;
  }

  public LoanAndRelatedRecords(JsonObject loan) {
    this(loan, null, null, null, null, null, null, null);
  }

  public JsonObject getLoan() {
    return loan;
  }

  public LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return new LoanAndRelatedRecords(loan, new InventoryRecords(updatedItem,
      inventoryRecords.getHolding(), inventoryRecords.getInstance()),
      requestQueue, requestingUser, proxyingUser, loanPolicy, location,
      this.materialType);
  }

  public LoanAndRelatedRecords withLoan(JsonObject newLoan) {
    return new LoanAndRelatedRecords(newLoan, inventoryRecords, requestQueue,
      requestingUser, proxyingUser, loanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withRequestingUser(JsonObject newUser) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      newUser, proxyingUser, loanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withProxyingUser(JsonObject newProxyingUser) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, newProxyingUser, loanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withLoanPolicy(JsonObject newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, proxyingUser, newLoanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, newRequestQueue,
      requestingUser, proxyingUser, loanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withLocation(JsonObject newLocation) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, proxyingUser, loanPolicy, newLocation, this.materialType);
  }

  public LoanAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    return new LoanAndRelatedRecords(loan, newInventoryRecords, requestQueue,
      requestingUser, proxyingUser, loanPolicy, location, this.materialType);
  }

  public LoanAndRelatedRecords withMaterialType(JsonObject newMaterialType) {
    return new LoanAndRelatedRecords(loan, inventoryRecords, requestQueue,
      requestingUser, proxyingUser, loanPolicy, location, newMaterialType);
  }
}
