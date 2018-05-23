package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

public class Request implements ItemRelatedRecord {
  private final JsonObject representation;

  public Request(JsonObject representation) {
    this.representation = representation;
  }

  public String getString(String propertyName) {
    return representation.getString(propertyName);
  }

  public JsonObject asJson() {
    return representation.copy();
  }

  public void put(String propertyName, String value) {
    representation.put(propertyName, value);
  }

  boolean isFulfillable() {
    return StringUtils.equals(getString("fulfilmentPreference"),
      RequestFulfilmentPreference.HOLD_SHELF);
  }

  boolean isOpen() {
    String status = getString("status");

    return StringUtils.equals(status, RequestStatus.OPEN_AWAITING_PICKUP)
      || StringUtils.equals(status, RequestStatus.OPEN_NOT_YET_FILLED);
  }

  @Override
  public String getItemId() {
    return representation.getString("itemId");
  }

  String getRequesterId() {
    return getString("requesterId");
  }

  String getProxyUserId() {
    return getString("proxyUserId");
  }
}
