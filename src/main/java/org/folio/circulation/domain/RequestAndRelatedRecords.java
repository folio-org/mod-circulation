package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.RequestPolicy;

public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  private final Request request;
  private final RequestQueue requestQueue;
  private final RequestPolicy requestPolicy;
  
  private final MoveRequestRecord moveRequestRecord;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    RequestPolicy requestPolicy,
    MoveRequestRecord moveRequestRecord) {

    this.request = request;
    this.requestQueue = requestQueue;
    this.requestPolicy = requestPolicy;
    this.moveRequestRecord = moveRequestRecord;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null, null);
  }

  public RequestAndRelatedRecords withRequest(Request newRequest) {
    return new RequestAndRelatedRecords(
      newRequest.withItem(request.getItem()),
      this.requestQueue,
      null,
      this.moveRequestRecord
    );
  }

  public RequestAndRelatedRecords withRequestPolicy(RequestPolicy newRequestPolicy) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newRequestPolicy,
      this.moveRequestRecord
    );
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.requestPolicy,
      this.moveRequestRecord
    );
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord
    );
  }

  public RequestAndRelatedRecords withLoan(Loan newLoan) {
    return new RequestAndRelatedRecords(
      this.request.withLoan(newLoan),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord
    );
  }
  
  public RequestAndRelatedRecords withRequestType(RequestType newRequestType) {
    return new RequestAndRelatedRecords(
      this.request.withRequestType(newRequestType),
      this.requestQueue,
      this.requestPolicy,
      this.moveRequestRecord
    );
  }

  public RequestAndRelatedRecords asMove(String originalItemId, String destinationItemId) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      this.requestPolicy,
      MoveRequestRecord.with(originalItemId, destinationItemId)
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
}
