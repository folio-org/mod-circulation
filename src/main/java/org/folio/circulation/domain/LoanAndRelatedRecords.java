package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.JsonPropertyWriter;
import org.joda.time.DateTimeZone;

public class LoanAndRelatedRecords implements UserRelatedRecord {
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

  public LoanAndRelatedRecords withItemEffectiveLocationIdAtCheckOut() {
    Item item = this.loan.getItem();
    return withLoan(loan.changeItemEffectiveLocationIdAtCheckOut(item.getLocationId()));
  }

  public LoanAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new LoanAndRelatedRecords(loan, requestQueue, newTimeZone);
  }

  public JsonObject asJson() {
    JsonObject json = new JsonObject();
    JsonPropertyWriter.write(json, "loan", loan.asJson());
    JsonPropertyWriter.write(json, "requestQue", JsonObject.mapFrom(requestQueue));
    JsonPropertyWriter.write(json, "timeZone", JsonObject.mapFrom(timeZone));
    return json;
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
