package org.folio.circulation.domain;

import java.time.ZoneId;
import java.time.ZoneOffset;

import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;
import org.folio.circulation.domain.policy.RequestPolicy;

public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  private final Request request;
  private final Request originalRequest;
  private final RequestQueue requestQueue;
  private final RequestPolicy requestPolicy;
  private final ZoneId timeZone;
  private final MoveRequestRecord moveRequestRecord;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    RequestPolicy requestPolicy,
    MoveRequestRecord moveRequestRecord,
    ZoneId timeZone) {

    this.request = request;
    this.originalRequest = request.copy();
    this.requestQueue = requestQueue;
    this.requestPolicy = requestPolicy;
    this.timeZone = timeZone;
    this.moveRequestRecord = moveRequestRecord;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null, null, ZoneOffset.UTC);
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

  RequestAndRelatedRecords withTimeZone(ZoneId newTimeZone) {
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

  ZoneId getTimeZone() {
    return timeZone;
  }

  public Request getOriginalRequest() {
    return originalRequest;
  }
}
