package org.folio.circulation.domain.representations;

public class RequestProperties {
  private RequestProperties() { }

  public static final String STATUS = "status";
  public static final String NAME = "name";
  public static final String PROXY_USER_ID = "proxyUserId";
  public static final String POSITION = "position";
  public static final String HOLD_SHELF_EXPIRATION_DATE = "holdShelfExpirationDate";
  public static final String REQUEST_EXPIRATION_DATE = "requestExpirationDate";
  public static final String CANCELLATION_ADDITIONAL_INFORMATION = "cancellationAdditionalInformation";
  public static final String CANCELLATION_REASON_ID = "cancellationReasonId";
}
