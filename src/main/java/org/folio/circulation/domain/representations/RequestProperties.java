package org.folio.circulation.domain.representations;

public class RequestProperties {
  private RequestProperties() { }

  public static final String STATUS = "status";
  public static final String INSTANCE_ID = "instanceId";
  public static final String ITEM_ID = "itemId";
  public static final String HOLDINGS_RECORD_ID = "holdingsRecordId";
  public static final String REQUEST_LEVEL = "requestLevel";
  public static final String REQUEST_TYPE = "requestType";
  public static final String ECS_REQUEST_PHASE = "ecsRequestPhase";
  public static final String PROXY_USER_ID = "proxyUserId";
  public static final String POSITION = "position";
  public static final String HOLD_SHELF_EXPIRATION_DATE = "holdShelfExpirationDate";
  public static final String REQUEST_DATE = "requestDate";
  public static final String REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  public static final String CANCELLATION_ADDITIONAL_INFORMATION = "cancellationAdditionalInformation";
  public static final String CANCELLATION_REASON_ID = "cancellationReasonId";
  public static final String CANCELLATION_REASON_NAME = "name";
  public static final String CANCELLATION_REASON_PUBLIC_DESCRIPTION = "publicDescription";
  public static final String REQUESTER_ID = "requesterId";
  public static final String FULFILLMENT_PREFERENCE = "fulfillmentPreference";
  public static final String PICKUP_SERVICE_POINT_ID = "pickupServicePointId";
  public static final String ITEM_LOCATION_CODE = "itemLocationCode";
}
