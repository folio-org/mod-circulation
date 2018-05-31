package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.InventoryRecords;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final User requestingUser;
  private final User proxyingUser;
  private final LoanPolicy loanPolicy;
  private final JsonObject materialType;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    User requestingUser,
    User proxyingUser,
    LoanPolicy loanPolicy,
    JsonObject materialType) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.proxyingUser = proxyingUser;
    this.loanPolicy = loanPolicy;
    this.materialType = materialType;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, null, null);
  }

  LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return withInventoryRecords(loan.getInventoryRecords().updateItem(updatedItem));
  }

  LoanAndRelatedRecords withLoan(Loan newLoan) {
    //TODO: find a better way to carry over related records
    newLoan.setInventoryRecords(loan.getInventoryRecords());

    return new LoanAndRelatedRecords(newLoan, requestQueue,
      requestingUser, proxyingUser, loanPolicy, this.materialType);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newUser, proxyingUser, loanPolicy, this.materialType);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxyingUser) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, newProxyingUser, loanPolicy, this.materialType);
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, newLoanPolicy, this.materialType);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      requestingUser, proxyingUser, loanPolicy, this.materialType);
  }

  LoanAndRelatedRecords withLocation() {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy, this.materialType);
  }

  public LoanAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    loan.setInventoryRecords(newInventoryRecords);

    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy, this.materialType);
  }

  LoanAndRelatedRecords withMaterialType(JsonObject newMaterialType) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy, newMaterialType);
  }

  public InventoryRecords getInventoryRecords() {
    return loan.getInventoryRecords();
  }

  public Loan getLoan() {
    return loan;
  }

  RequestQueue getRequestQueue() {
    return requestQueue;
  }

  public User getRequestingUser() {
    return requestingUser;
  }

  public User getProxyingUser() {
    return proxyingUser;
  }

  public LoanPolicy getLoanPolicy() {
    return loanPolicy;
  }

  public JsonObject getMaterialType() {
    return materialType;
  }

  @Override
  public String getUserId() {
    return loan.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return loan.getProxyUserId();
  }
}
