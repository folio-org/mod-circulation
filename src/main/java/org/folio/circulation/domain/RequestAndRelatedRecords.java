package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.RequestPolicy;

public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  private final Request request;
  private final RequestQueue requestQueue;
  private final RequestPolicy requestPolicy;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    RequestPolicy requestPolicy) {

    this.request = request;
    this.requestQueue = requestQueue;
    this.requestPolicy = requestPolicy;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null);
  }

  RequestAndRelatedRecords withRequest(Request newRequest) {
    return new RequestAndRelatedRecords(newRequest.withItem(request.getItem()),
      this.requestQueue, null
    );
  }

  public RequestAndRelatedRecords withRequestPolicy(RequestPolicy newRequestPolicy) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newRequestPolicy
    );
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.requestPolicy
    );
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue,
      this.requestPolicy
    );
  }

  public Request getRequest() {
    return request;
  }

  RequestQueue getRequestQueue() {
    return requestQueue;
  }

  RequestPolicy getRequestPolicy() {return requestPolicy; }

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
