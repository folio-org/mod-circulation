package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.Result.succeeded;
import static org.folio.circulation.domain.RequestFulfilmentPreference.NONE;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getUUIDProperty;
import static org.folio.circulation.support.ValidationErrorFailure.failedValidation;

import java.util.UUID;

import org.folio.circulation.domain.RequestFulfilmentPreference;
import org.folio.circulation.support.Result;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class RequestByInstanceIdRequest {
  private static final String JSON_FIELD_REQUEST_DATE = "requestDate";
  private static final String JSON_FIELD_REQUESTER_ID = "requesterId";
  private static final String JSON_FIELD_PROXY_USER_ID = "proxyUserId";
  private static final String JSON_FIELD_INSTANCE_ID = "instanceId";
  private static final String JSON_FIELD_FULFILMENT_PREFERENCE = "fulfilmentPreference";
  private static final String JSON_FIELD_DELIVERY_ADDRESS_TYPE_ID = "deliveryAddressTypeId";
  private static final String JSON_FIELD_REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  private static final String JSON_FIELD_HOLD_SHELF_EXPIRATION_DATE = "holdShelfExpirationDate";
  private static final String JSON_FIELD_PICKUP_SERVICE_POINT_ID = "pickupServicePointId";

  private final DateTime requestDate;
  private final UUID requesterId;
  private final UUID proxyUserId;
  private final String instanceId;
  private final RequestFulfilmentPreference fulfilmentPreference;
  private final UUID deliveryAddressTypeId;
  private final DateTime requestExpirationDate;
  private final DateTime holdShelfExpirationDate;
  private final String pickupServicePointId;

  @SuppressWarnings("squid:S00107")
  private RequestByInstanceIdRequest(
      DateTime requestDate,
      UUID requesterId,
      UUID proxyUserId,
      String instanceId,
      RequestFulfilmentPreference fulfilmentPreference,
      UUID deliveryAddressTypeId,
      DateTime requestExpirationDate,
      DateTime holdShelfExpirationDate,
      String pickupServicePointId) {
    this.requestDate = requestDate;
    this.requesterId = requesterId;
    this.proxyUserId = proxyUserId;
    this.instanceId = instanceId;
    this.fulfilmentPreference = fulfilmentPreference;
    this.deliveryAddressTypeId = deliveryAddressTypeId;
    this.requestExpirationDate = requestExpirationDate;
    this.holdShelfExpirationDate = holdShelfExpirationDate;
    this.pickupServicePointId = pickupServicePointId;
  }

  public static Result<RequestByInstanceIdRequest> from(JsonObject json) {
    final DateTime requestDate = getDateTimeProperty(json, JSON_FIELD_REQUEST_DATE);

    if (requestDate == null) {
      return failedValidation("Request must have a request date", JSON_FIELD_REQUEST_DATE, null);
    }

    final UUID requesterId = getUUIDProperty(json, JSON_FIELD_REQUESTER_ID);

    if (requesterId == null) {
      return failedValidation("Request must have a requester id", JSON_FIELD_REQUESTER_ID, null);
    }

    final String instanceId = getProperty(json, JSON_FIELD_INSTANCE_ID);

    if (instanceId == null) {
      return failedValidation("Request must have an instance id", JSON_FIELD_INSTANCE_ID, null);
    }

    final RequestFulfilmentPreference fulfilmentPreference =
        RequestFulfilmentPreference.from(getProperty(json, JSON_FIELD_FULFILMENT_PREFERENCE));

    if (fulfilmentPreference == NONE) {
      return failedValidation("Request must have a fulfillment preference", JSON_FIELD_FULFILMENT_PREFERENCE, null);
    }

    final UUID proxyUserId = getUUIDProperty(json, JSON_FIELD_PROXY_USER_ID);
    final UUID deliveryAddressTypeId = getUUIDProperty(json, JSON_FIELD_DELIVERY_ADDRESS_TYPE_ID);
    final DateTime requestExpirationDate = getDateTimeProperty(json, JSON_FIELD_REQUEST_EXPIRATION_DATE);
    final DateTime holdShelfExpirationDate = getDateTimeProperty(json, JSON_FIELD_HOLD_SHELF_EXPIRATION_DATE);
    final String pickupServicePointId = getProperty(json, JSON_FIELD_PICKUP_SERVICE_POINT_ID);

    return succeeded(new RequestByInstanceIdRequest(requestDate, requesterId, proxyUserId, instanceId,
        fulfilmentPreference, deliveryAddressTypeId, requestExpirationDate, holdShelfExpirationDate,
        pickupServicePointId));
  }

  public DateTime getRequestDate() {
    return requestDate;
  }

  public UUID getRequesterId() {
    return requesterId;
  }

  public UUID getProxyUserId() {
    return proxyUserId;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public RequestFulfilmentPreference getFulfilmentPreference() {
    return fulfilmentPreference;
  }

  public UUID getDeliveryAddressTypeId() {
    return deliveryAddressTypeId;
  }

  public DateTime getRequestExpirationDate() {
    return requestExpirationDate;
  }

  public DateTime getHoldShelfExpirationDate() {
    return holdShelfExpirationDate;
  }

  public String getPickupServicePointId() {
    return pickupServicePointId;
  }
}
