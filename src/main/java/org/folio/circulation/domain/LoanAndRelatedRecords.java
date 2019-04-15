package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;
import org.joda.time.DateTimeZone;

public class LoanAndRelatedRecords implements UserRelatedRecord {

  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final DateTimeZone timeZone;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy,
    DateTimeZone timeZone) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.timeZone = timeZone;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, timeZone);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue, newLoanPolicy, timeZone);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,

      loanPolicy, timeZone);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newTimeZone);
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

  public DateTimeZone getTimeZone() {
    return timeZone;
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
