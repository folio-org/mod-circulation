package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;

public class RequestAndRelatedRecords {
  private final Request request;
  private final RequestQueue requestQueue;
  private final User requestingUser;
  private final User proxyUser;

  private RequestAndRelatedRecords(
    Request request,
    RequestQueue requestQueue,
    User requestingUser,
    User proxyUser) {

    this.request = request;
    this.requestQueue = requestQueue;
    this.requestingUser = requestingUser;
    this.proxyUser = proxyUser;
  }

  public RequestAndRelatedRecords(Request request) {
    this(request, null, null, null);
  }

  RequestAndRelatedRecords withItem(JsonObject updatedItem) {
    return withInventoryRecords(new InventoryRecords(updatedItem,
      getInventoryRecords().holding, getInventoryRecords().instance));
  }

  public RequestAndRelatedRecords withRequest(Request newRequest) {
    newRequest.setInventoryRecords(request.getInventoryRecords());

    return new RequestAndRelatedRecords(newRequest,
      this.requestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withRequestQueue(RequestQueue newRequestQueue) {
    return new RequestAndRelatedRecords(
      this.request,
      newRequestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withInventoryRecords(InventoryRecords newInventoryRecords) {
    this.request.setInventoryRecords(newInventoryRecords);

    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      this.requestingUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withRequestingUser(User newUser) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      newUser,
      this.proxyUser);
  }

  public RequestAndRelatedRecords withProxyUser(User newProxyUser) {
    return new RequestAndRelatedRecords(
      this.request,
      this.requestQueue,
      this.requestingUser,
      newProxyUser);
  }

  public Request getRequest() {
    return request;
  }

  public InventoryRecords getInventoryRecords() {
    return request.getInventoryRecords();
  }

  RequestQueue getRequestQueue() {
    return requestQueue;
  }

  public User getRequestingUser() {
    return requestingUser;
  }

  public User getProxyUser() {
    return proxyUser;
  }
}
