package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.policy.LoanPolicy;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final LoanPolicy loanPolicy;
  private final Calendar calendar;
  private final LibraryHours libraryHours;

  private LoanAndRelatedRecords(
    Loan loan,
    RequestQueue requestQueue,
    LoanPolicy loanPolicy, Calendar calendar,
    LibraryHours libraryHours) {

    this.loan = loan;
    this.requestQueue = requestQueue;
    this.loanPolicy = loanPolicy;
    this.calendar = calendar;
    this.libraryHours = libraryHours;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, null, null, null, null);
  }

  LoanAndRelatedRecords withItem(JsonObject updatedItem) {
    return withItem(loan.getItem().updateItem(updatedItem));
  }

  LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, loanPolicy, calendar, libraryHours);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  LoanAndRelatedRecords withCalendar(Calendar newCalendar) {
    return new LoanAndRelatedRecords(loan, requestQueue, loanPolicy, newCalendar, libraryHours);
  }

  LoanAndRelatedRecords withLibraryHours(LibraryHours newLibraryHours) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      loanPolicy, calendar, newLibraryHours);
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withLoanPolicy(LoanPolicy newLoanPolicy) {
    return new LoanAndRelatedRecords(loan, requestQueue,
      newLoanPolicy, calendar, libraryHours);
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue,
      loanPolicy, calendar, libraryHours);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
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

  public LibraryHours getLibraryHours() {
    return libraryHours;
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
