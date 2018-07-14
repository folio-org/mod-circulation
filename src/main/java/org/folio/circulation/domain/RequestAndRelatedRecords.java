package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class RequestAndRelatedRecords implements UserRelatedRecord {
  private final Request request;
  private final RequestQueue requestQueue;
  private final User proxy;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    User proxy) {

    this.request = request;
    this.requestQueue = requestQueue;
    this.proxy = proxy;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null);
  }

  RequestAndRelatedRecords withItem(JsonObject updatedItem) {
    return withItem(request.getItem().updateItem(updatedItem));
  }

  public RequestAndRelatedRecords withRequest(Request newRequest) {
    return new RequestAndRelatedRecords(newRequest.withItem(request.getItem()),
      this.requestQueue,
      this.proxy);
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.proxy);
  }

  public RequestAndRelatedRecords withItem(Item newItem) {
    return new RequestAndRelatedRecords(
      this.request.withItem(newItem),
      this.requestQueue,
      this.proxy);
  }

  public RequestAndRelatedRecords withRequestingUser(User newRequester) {
    return new RequestAndRelatedRecords(
      this.request.withRequester(newRequester),
      this.requestQueue,
      this.proxy);
  }

  public RequestAndRelatedRecords withProxyUser(User newProxy) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newProxy);
  }

  public Request getRequest() {
    return request;
  }

  public RequestQueue getRequestQueue() {
    return requestQueue;
  }

  User getProxy() {
    return proxy;
  }

  @Override
  public String getUserId() {
    return request.getUserId();
  }

  @Override
  public String getProxyUserId() {
    return request.getProxyUserId();
  }
}
