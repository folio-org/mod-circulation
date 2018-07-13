package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.folio.circulation.domain.representations.RequestProperties;

import static org.folio.circulation.domain.RequestStatus.*;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getIntegerProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;
  private final Item item;

  public Request(JsonObject representation, Item item) {
    this.representation = representation;
    this.item = item;
  }

  public static Request from(JsonObject representation) {
    return from(representation, null);
  }

  public static Request from(JsonObject representation, Item item) {
    return new Request(representation, item);
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  boolean isFulfillable() {
    return StringUtils.equals(getFulfilmentPreference(),
      RequestFulfilmentPreference.HOLD_SHELF);
  }

  boolean isOpen() {
    String status = getStatus();

    return StringUtils.equals(status, OPEN_AWAITING_PICKUP)
      || StringUtils.equals(status, OPEN_NOT_YET_FILLED);
  }

  public boolean isCancelled() {
    return StringUtils.equals(getStatus(), CLOSED_CANCELLED);
  }

  boolean isFor(User user) {
    return StringUtils.equals(getUserId(), user.getId());
  }

  boolean isAwaitingPickup() {
    return StringUtils.equals(getStatus(), OPEN_AWAITING_PICKUP);
  }

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  public Request withItem(Item newItem) {
    return new Request(representation, newItem);
  }

  @Override
  public String getUserId() {
    return representation.getString("requesterId");
  }

  @Override
  public String getProxyUserId() {
    return representation.getString("proxyUserId");
  }

  String getFulfilmentPreference() {
    return representation.getString("fulfilmentPreference");
  }

  String getId() {
    return representation.getString("id");
  }

  String getRequestType() {
    return representation.getString("requestType");
  }

  String getStatus() {
    return representation.getString(STATUS);
  }

  void changeStatus(String status) {
    representation.put(STATUS, status);
  }

  public Item getItem() {
    return item;
  }

  public Request changePosition(Integer newPosition) {
    write(representation, RequestProperties.POSITION, newPosition);
    return this;
  }

  public Request removePosition() {
    representation.remove(RequestProperties.POSITION);
    return this;
  }

  public Integer getPosition() {
    return getIntegerProperty(representation, RequestProperties.POSITION, null);
  }
}
