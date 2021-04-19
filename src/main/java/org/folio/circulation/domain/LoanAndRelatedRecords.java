package org.folio.circulation.domain;

import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;

public class LoanAndRelatedRecords implements UserRelatedRecord {
  private final Loan loan;
  private final RequestQueue requestQueue;
  private final DateTimeZone timeZone;
  private final JsonObject logContextProperties;
  private final String loggedInUserPersonalName;

  private LoanAndRelatedRecords(Loan loan, RequestQueue requestQueue, DateTimeZone timeZone, JsonObject logContextProperties, String loggedInUserPersonalName) {
    this.loan = loan;
    this.requestQueue = requestQueue;
    this.timeZone = timeZone;
    this.logContextProperties = logContextProperties;
    this.loggedInUserPersonalName = loggedInUserPersonalName;
  }

  public LoanAndRelatedRecords(Loan loan) {
    this(loan, DateTimeZone.UTC);
  }

  public LoanAndRelatedRecords(Loan loan, DateTimeZone timeZone) {
    this(loan, null, timeZone, new JsonObject(), null);
  }

  public LoanAndRelatedRecords changeItemStatus(ItemStatus status) {
    return withItem(getItem().changeStatus(status));
  }

  public LoanAndRelatedRecords withLoan(Loan newLoan) {
    return new LoanAndRelatedRecords(newLoan, requestQueue, timeZone, logContextProperties, loggedInUserPersonalName);
  }

  public LoanAndRelatedRecords withRequestingUser(User newUser) {
    return withLoan(loan.withUser(newUser));
  }

  public LoanAndRelatedRecords withProxyingUser(User newProxy) {
    return withLoan(loan.withProxy(newProxy));
  }

  public LoanAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new LoanAndRelatedRecords(loan, newRequestQueue, timeZone, logContextProperties, loggedInUserPersonalName);
  }

  public LoanAndRelatedRecords withItem(Item newItem) {
    return withLoan(loan.withItem(newItem));
  }

  public LoanAndRelatedRecords withItemEffectiveLocationIdAtCheckOut() {
    return withLoan(loan.changeItemEffectiveLocationIdAtCheckOut(getItem().getLocationId()));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, newTimeZone, logContextProperties, loggedInUserPersonalName);
  }

  public LoanAndRelatedRecords withLoggedInUserPersonalName(String name) {
    return new LoanAndRelatedRecords(loan, requestQueue, timeZone, logContextProperties, name);
  }

  public Loan getLoan() {
    return loan;
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

  public String getLoggedInUserPersonalName() {
    return loggedInUserPersonalName;
  }
}
