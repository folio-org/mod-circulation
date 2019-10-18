package org.folio.circulation.domain;

import java.util.List;

public class HoldShelfClearanceRequestContext {

  private List<String> awaitingPickupItemIds;
  private List<String> awaitingPickupRequestItemIds;
  private List<Request> expiredOrCancelledRequests;

  public HoldShelfClearanceRequestContext withAwaitingPickupItemIds(List<String> itemIds) {
    this.awaitingPickupItemIds = itemIds;
    return this;
  }

  public HoldShelfClearanceRequestContext withAwaitingPickupRequestItemIds(List<String> itemIds) {
    this.awaitingPickupRequestItemIds = itemIds;
    return this;
  }

  public HoldShelfClearanceRequestContext withExpiredOrCancelledRequests(List<Request> requests) {
    this.expiredOrCancelledRequests = requests;
    return this;
  }

  public List<String> getAwaitingPickupItemIds() {
    return awaitingPickupItemIds;
  }

  public List<String> getAwaitingPickupRequestItemIds() {
    return awaitingPickupRequestItemIds;
  }

  public List<Request> getExpiredOrCancelledRequests() {
    return expiredOrCancelledRequests;
  }

}
