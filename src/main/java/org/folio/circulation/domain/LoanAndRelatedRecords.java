package org.folio.circulation.domain;

import org.joda.time.DateTimeZone;

public class LoanAndRelatedRecords implements UserRelatedRecord, LoanRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final DateTimeZone timeZone;

  private LoanAndRelatedRecords(Loan loan, RequestQueue requestQueue, DateTimeZone timeZone) {
    this.loan = loan;
    this.requestQueue = requestQueue;
    this.timeZone = timeZone;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, DateTimeZone timeZone) {
    this(loan, null, timeZone);
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, timeZone);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      timeZone);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, newTimeZone);
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
