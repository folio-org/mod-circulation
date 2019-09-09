package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.RequestPolicy;
import org.joda.time.DateTimeZone;

public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  private final Request request;
  private final RequestQueue requestQueue;
  private final RequestPolicy requestPolicy;
  private final DateTimeZone timeZone;

  private final MoveRequestRecord moveRequestRecord;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    RequestPolicy requestPolicy,
    MoveRequestRecord moveRequestRecord,
    DateTimeZone timeZone) {

    this.request = request;
    this.requestQueue = requestQueue;
    this.requestPolicy = requestPolicy;
    this.timeZone = timeZone;
    this.moveRequestRecord = moveRequestRecord;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null, null, DateTimeZone.UTC);
  }

  public RequestAndRelatedRecords withRequest(Request newRequest) {
    return new RequestAndRelatedRecords(
      newRequest.withItem(request.getItem()),
      this.requestQueue,
      null,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  public RequestAndRelatedRecords withRequestPolicy(RequestPolicy newRequestPolicy) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newRequestPolicy,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  public RequestAndRelatedRecords withLoan(Loan newLoan) {
    return new RequestAndRelatedRecords(
      this.request.withLoan(newLoan),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  public RequestAndRelatedRecords withRequestType(RequestType newRequestType) {
    return new RequestAndRelatedRecords(
      this.request.withRequestType(newRequestType),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord,
      this.timeZone
    );
  }

  RequestAndRelatedRecords withTimeZone(DateTimeZone newTimeZone) {
    return new RequestAndRelatedRecords(request, requestQueue, requestPolicy,
      moveRequestRecord, newTimeZone);
  }

  public RequestAndRelatedRecords asMove(String originalItemId, String destinationItemId) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      this.requestPolicy,
      MoveRequestRecord.with(originalItemId, destinationItemId), timeZone
    );
  }

  public Request getRequest() {
    return request;
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }

  RequestPolicy getRequestPolicy() { return requestPolicy; }

  String getSourceItemId() {
    return moveRequestRecord != null ? moveRequestRecord.getSourceItemId() : null;
  }

  String getDestinationItemId() {
    return moveRequestRecord != null ? moveRequestRecord.getDestinationItemId() : null;
  }

  @Override
  public String getUserId() {
    return request.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return request.getProxyUserId();
  }

  @Override
  public String getItemId() {
    return request.getItemId();
  }

  DateTimeZone getTimeZone() {
    return timeZone;
  }
}
