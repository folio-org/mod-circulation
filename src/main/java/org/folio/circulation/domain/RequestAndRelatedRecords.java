package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class RequestAndRelatedRecords implements UserRelatedRecord, ItemRelatedRecord {
  private final Request request;
  private final RequestQueue requestQueue;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue) {

    this.request = request;
    this.requestQueue = requestQueue;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null);
  }

  RequestAndRelatedRecords withItem(JsonObject updatedItem) {
    return withItem(request.getItem().updateItem(updatedItem));
  }

  RequestAndRelatedRecords withRequest(Request newRequest) {
    return new RequestAndRelatedRecords(newRequest.withItem(request.getItem()),
      this.requestQueue
    );
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue
    );
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue
    );
  }

  public Request getRequest() {
    return request;
  }

  RequestQueue getRequestQueue() {
    return requestQueue;
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
