package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicy;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final User proxyingUser;
  private final LoanPolicy loanPolicy;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    User proxyingUser,
    LoanPolicy loanPolicy) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.proxyingUser = proxyingUser;
    this.loanPolicy = loanPolicy;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null);
  }

  LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return withItem(loan.getItem().updateItem(updatedItem));
  }

  LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue,
      proxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(Loan.from(loan.asJson(), loan.getItem(), newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxyingUser) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newProxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      proxyingUser, newLoanPolicy);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      proxyingUser, loanPolicy);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(Loan.from(loan.asJson(), newItem, loan.getUser()));
  }

  public Loan getLoan() {
    return loan;
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
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
