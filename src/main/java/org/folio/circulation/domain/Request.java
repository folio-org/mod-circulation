package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import static org.folio.circulation.domain.RequestStatus.OPEN_AWAITING_PICKUP;
import static org.folio.circulation.domain.representations.RequestProperties.STATUS;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;

  private Item item;

  public Request(JsonObject representation) {
    this.representation = representation;
  }

  public static Request from(JsonObject representation, Item item) {
    final Request request = new Request(representation);

    request.setItem(item);

    return request;
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  boolean isFulfillable() {
    return StringUtils.equals(getFulfilmentPreference(),
      RequestFulfilmentPreference.HOLD_SHELF);
  }

  boolean isOpen() {
    String status = representation.getString(STATUS);

    return StringUtils.equals(status, RequestStatus.OPEN_AWAITING_PICKUP)
      || StringUtils.equals(status, RequestStatus.OPEN_NOT_YET_FILLED);
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

  public Request withItem(Item item) {
    final Request request = new Request(representation);

    request.setItem(item);

    return request;
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

  void setItem(Item item) {
    this.item = item;
  }

  public String getCancellationReasonId() {
    return getProperty(representation, "cancellationReasonId");
  }
  
  public String getCancelledByUserId() {
    return getProperty(representation, "cancelledByUserId");
  }
  
  public DateTime getCancelledDate() {
    return getDateTimeProperty(representation, "cancelledDate");
  }
  
  public String getCancellationAdditionalInformation() {
    return getProperty(representation, "cancellationAdditionalInformation");
  }
  
}
