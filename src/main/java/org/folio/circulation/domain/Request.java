package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import static org.folio.circulation.domain.representations.RequestProperties.STATUS;

public class Request implements ItemRelatedRecord, UserRelatedRecord {
  private final JsonObject representation;

  public Request(JsonObject representation) {
    this.representation = representation;
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

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  @Override
  public String getRequesterId() {
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
}
