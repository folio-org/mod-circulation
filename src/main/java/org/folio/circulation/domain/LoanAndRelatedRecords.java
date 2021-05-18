package org.folio.circulation.domain;

import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  public static final String REASON_TO_OVERRIDE = "reasonToOverride";

  private final Loan loan;
  private final Loan existingLoan;
  private final RequestQueue requestQueue;
  private final DateTimeZone timeZone;
  private final JsonObject logContextProperties;
  private final String loggedInUserId;

  private LoanAndRelatedRecords(Loan loan, Loan existingLoan, RequestQueue requestQueue, DateTimeZone timeZone, JsonObject logContextProperties, String loggedInUserId) {
    this.loan = loan;
    this.existingLoan = existingLoan;
    this.requestQueue = requestQueue;
    this.timeZone = timeZone;
    this.logContextProperties = logContextProperties;
    this.loggedInUserId = loggedInUserId;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, Loan existingLoan) {
    this(loan, existingLoan, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, Loan existingLoan, DateTimeZone timeZone) {
    this(loan, existingLoan, null, timeZone, new JsonObject(), null);
  }

  public LoanAndRelatedRecords(Loan loan, DateTimeZone timeZone) {
    this(loan, null, null, timeZone, new JsonObject(), null);
  }

  public LoanAndRelatedRecords changeItemStatus(ItemStatus status) {
    return withItem(getItem().changeStatus(status));
  }


  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, existingLoan, requestQueue, timeZone, logContextProperties, loggedInUserId);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, existingLoan, newRequestQueue,
      timeZone, logContextProperties, loggedInUserId);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withItemEffectiveLocationIdAtCheckOut() {
    return withLoan(loan.changeItemEffectiveLocationIdAtCheckOut(getItem().getLocationId()));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, existingLoan, requestQueue, newTimeZone, logContextProperties, loggedInUserId);
  }

  public LoanAndRelatedRecords withLoggedInUserId(String loggedInUserId) {
    return new LoanAndRelatedRecords(loan, existingLoan, requestQueue, timeZone, logContextProperties, loggedInUserId);
  }

  public Loan getLoan() {
    return loan;
  }

  public Loan getExistingLoan() {
    return existingLoan;
  }

  public Item getItem() {
    return getLoan().getItem();
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

  public String getLoggedInUserId() {
    return loggedInUserId;
  }

  @Override
  public String getUserId() {
    return loan.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return loan.getProxyUserId();
  }

  public JsonObject getLogContextProperties() {
    return logContextProperties;
  }
}
