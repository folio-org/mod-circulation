package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      loanPolicy);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public Loan getLoan() {
    return loan;
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }

  public User getProxy() {
    return loan.getProxy();
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
