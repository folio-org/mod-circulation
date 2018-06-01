package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.folio.circulation.support.Item;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final User requestingUser;
  private final User proxyingUser;
  private final LoanPolicy loanPolicy;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    User requestingUser,
    User proxyingUser,
    LoanPolicy loanPolicy) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.proxyingUser = proxyingUser;
    this.loanPolicy = loanPolicy;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, null);
  }

  LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return withInventoryRecords(loan.getItem().updateItem(updatedItem));
  }

  LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue,
      requestingUser, proxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newUser, proxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxyingUser) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, newProxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, newLoanPolicy);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      requestingUser, proxyingUser, loanPolicy);
  }

  LoanAndRelatedRecords withLocation() {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withInventoryRecords(Item newItem) {
    loan.setItem(newItem);

    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy);
  }

  LoanAndRelatedRecords withMaterialType() {
    return new LoanAndRelatedRecords(loan, requestQueue,
      requestingUser, proxyingUser, loanPolicy);
  }

  public Item getInventoryRecords() {
    return loan.getItem();
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

  @Override
  public String getUserId() {
    return loan.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return loan.getProxyUserId();
  }
}
