package org.folio.circulation.domain;

public class MoveRequestRecords {
  private Request request;
  private RequestQueue originalRequestQueue;
  private RequestQueue destinationRequestQueue;
  private Item originalItem;
  private Item destinationItem;

  private MoveRequestRecords(
      Request request,
      RequestQueue orignalRequestQueue,
      RequestQueue destinationRequestQueue,
      Item originalItem,
      Item destinationItem
    ) {
    this.request = request;
    this.originalRequestQueue = orignalRequestQueue;
    this.destinationRequestQueue = destinationRequestQueue;
    this.originalItem = originalItem;
    this.destinationItem = destinationItem;
  }

  public MoveRequestRecords() {
    this(null, null, null, null, null);
  }
  
  public MoveRequestRecords withRequest(Request request) {
    return new MoveRequestRecords(
        request,
        this.originalRequestQueue,
        this.destinationRequestQueue,
        this.originalItem,
        this.destinationItem
      );
  }

  public MoveRequestRecords withOriginalRequestQueue(RequestQueue originalRequestQueue) {
    return new MoveRequestRecords(
      this.request,
      originalRequestQueue,
      this.destinationRequestQueue,
      this.originalItem,
      this.destinationItem
    );
  }

  public MoveRequestRecords withDestinationRequestQueue(RequestQueue destinationRequestQueue) {
    return new MoveRequestRecords(
      this.request,
      this.originalRequestQueue,
      destinationRequestQueue,
      this.originalItem,
      this.destinationItem
    );
  }

  public MoveRequestRecords withOriginalItem(Item originalItem) {
    return new MoveRequestRecords(
      this.request,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      originalItem,
      this.destinationItem
    );
  }

  public MoveRequestRecords withDestinationItem(Item destinationItem) {
    return new MoveRequestRecords(
      this.request,
      this.originalRequestQueue,
      this.destinationRequestQueue,
      destinationItem,
      this.destinationItem
    );
  }

  public Request getRequest() {
    return this.request;
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
}
