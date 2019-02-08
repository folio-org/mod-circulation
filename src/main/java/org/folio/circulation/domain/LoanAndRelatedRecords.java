package org.folio.circulation.domain;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.policy.LoanPolicy;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final AdjustingOpeningDays initialDueDateDays;
  private final AdjustingOpeningDays fixedDueDateDays;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy, AdjustingOpeningDays initialDueDateDays,
    AdjustingOpeningDays fixedDueDateDays) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.initialDueDateDays = initialDueDateDays;
    this.fixedDueDateDays = fixedDueDateDays;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, null);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, initialDueDateDays, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  LoanAndRelatedRecords withInitialDueDateDays(AdjustingOpeningDays newCalendar) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newCalendar, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy, initialDueDateDays, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      loanPolicy, initialDueDateDays, fixedDueDateDays);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withFixedDueDateDays(AdjustingOpeningDays newFixedDueDateDays) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, initialDueDateDays, newFixedDueDateDays);
  }

  public Loan getLoan() {
    return loan;
  }

  public AdjustingOpeningDays getInitialDueDateDays() {
    return initialDueDateDays;
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

  public AdjustingOpeningDays getFixedDueDateDays() {
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
