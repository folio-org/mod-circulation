package org.folio.circulation.domain;

import org.folio.circulation.domain.policy.RequestPolicy;

public class MoveRequestRecords implements ItemRelatedRecord {
  private Request request;
  private Item item;
  private RequestQueue originalRequestQueue;
  private RequestQueue destinationRequestQueue;
  private Item originalItem;
  private Item destinationItem;
  private RequestPolicy requestPolicy;

  private MoveRequestRecords(
      Request request,
      Item item,
      RequestQueue orignalRequestQueue,
      RequestQueue destinationRequestQueue,
      Item originalItem,
      Item destinationItem,
      RequestPolicy requestPolicy
    ) {
    this.request = request;
    this.item = item;
    this.originalRequestQueue = orignalRequestQueue;
    this.destinationRequestQueue = destinationRequestQueue;
    this.originalItem = originalItem;
    this.destinationItem = destinationItem;
    this.requestPolicy = requestPolicy;
  }

  public MoveRequestRecords() {
    this(null, null, null, null, null, null, null);
  }
  
  public MoveRequestRecords withRequest(Request request) {
    return new MoveRequestRecords(
        request,
        this.item,
        this.originalRequestQueue,
        this.destinationRequestQueue,
        this.originalItem,
        this.destinationItem,
        this.requestPolicy
      );
  }

  public MoveRequestRecords withItem(Item item) {
    return new MoveRequestRecords(
      this.request,
      item,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      this.originalItem,
      this.destinationItem,
      this.requestPolicy
    );
  }

  public MoveRequestRecords withOriginalRequestQueue(RequestQueue originalRequestQueue) {
    return new MoveRequestRecords(
      this.request,
      this.item,
      originalRequestQueue,
      this.destinationRequestQueue,
      this.originalItem,
      this.destinationItem,
      this.requestPolicy
    );
  }

  public MoveRequestRecords withDestinationRequestQueue(RequestQueue destinationRequestQueue) {
    return new MoveRequestRecords(
      this.request,
      this.item,
      this.originalRequestQueue,
      destinationRequestQueue,
      this.originalItem,
      this.destinationItem,
      this.requestPolicy
    );
  }

  public MoveRequestRecords withOriginalItem(Item originalItem) {
    return new MoveRequestRecords(
      this.request,
      this.item,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      originalItem,
      this.destinationItem,
      this.requestPolicy
    );
  }

  public MoveRequestRecords withDestinationItem(Item destinationItem) {
    return new MoveRequestRecords(
      this.request,
      this.item,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      this.originalItem,
      destinationItem,
      this.requestPolicy
    );
  }

  public MoveRequestRecords withRequestPolicy(RequestPolicy requestPolicy) {
    return new MoveRequestRecords(
      this.request,
      this.item,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      this.originalItem,
      this.destinationItem,
      requestPolicy
    );
  }

  public void transferRequest(Request request) {
    this.originalRequestQueue.remove(request);
    this.destinationRequestQueue.getRequests().add(request);
  }

  public Request getRequest() {
    return this.request;
  }

  public Item getItem() {
    return this.item;
  }

  public RequestQueue getOriginalRequestQueue() {
    return this.originalRequestQueue;
  }

  public RequestQueue getDestinationRequestQueue() {
    return this.destinationRequestQueue;
  }

  public Item getOriginalItem() {
    return this.originalItem;
  }

  public Item getDestinationItem() {
    return this.destinationItem;
  }

  public RequestPolicy getRequestPolicy() {
    return this.requestPolicy;
  }

  @Override
  public String getItemId() {
    return this.request.getItemId();
  }
}
