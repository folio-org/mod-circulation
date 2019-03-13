package org.folio.circulation.domain;

import org.folio.circulation.AdjacentOpeningDays;
import org.folio.circulation.domain.policy.LoanPolicy;
import org.joda.time.DateTimeZone;

public class LoanAndRelatedRecords implements UserRelatedRecord {

  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final AdjacentOpeningDays adjacentOpeningDays;
  private final DateTimeZone timeZone;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy, AdjacentOpeningDays adjacentOpeningDays,
    DateTimeZone timeZone) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.adjacentOpeningDays = adjacentOpeningDays;
    this.timeZone = timeZone;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, adjacentOpeningDays, timeZone);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  LoanAndRelatedRecords withInitialDueDateDays(AdjacentOpeningDays newOpeningDays) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newOpeningDays, timeZone);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy, adjacentOpeningDays, timeZone);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,

      loanPolicy, adjacentOpeningDays, timeZone);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, adjacentOpeningDays, newTimeZone);
  }

  public Loan getLoan() {
    return loan;
  }

  public AdjacentOpeningDays getAdjacentOpeningDays() {
    return adjacentOpeningDays;
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
