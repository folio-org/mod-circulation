package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.LoanPolicy;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final Calendar calendar;
  private final Calendar fixedDueDateDays;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy, Calendar calendar,
    Calendar fixedDueDateDays) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.calendar = calendar;
    this.fixedDueDateDays = fixedDueDateDays;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, null);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, calendar, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  LoanAndRelatedRecords withCalendar(Calendar newCalendar) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newCalendar, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy, calendar, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      loanPolicy, calendar, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withFixedDueDateDays(Calendar newFixedDueDateDays) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, calendar, newFixedDueDateDays);
  }

  public Loan getLoan() {
    return loan;
  }

  public Calendar getCalendar() {
    return calendar;
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

  public Calendar getFixedDueDateDays() {
    return fixedDueDateDays;
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
