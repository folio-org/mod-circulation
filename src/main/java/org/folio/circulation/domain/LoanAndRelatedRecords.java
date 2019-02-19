package org.folio.circulation.domain;

import org.folio.circulation.AdjustingOpeningDays;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.joda.time.DateTimeZone;

public class LoanAndRelatedRecords implements UserRelatedRecord {

  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final AdjustingOpeningDays initialDueDateDays;
  private final DateTimeZone timeZone;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy, AdjustingOpeningDays initialDueDateDays,
    DateTimeZone timeZone) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.initialDueDateDays = initialDueDateDays;
    this.timeZone = timeZone;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, initialDueDateDays, timeZone);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  LoanAndRelatedRecords withInitialDueDateDays(AdjustingOpeningDays newOpeningDays) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newOpeningDays, timeZone);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy, initialDueDateDays, timeZone);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,

      loanPolicy, initialDueDateDays, timeZone);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, initialDueDateDays, newTimeZone);
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
