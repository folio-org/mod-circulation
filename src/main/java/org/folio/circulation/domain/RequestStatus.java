package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;

import org.folio.circulation.domain.representations.RequestProperties;

public enum RequestStatus {
  NONE(""),
  OPEN_NOT_YET_FILLED("Open - Not yet filled"),
  OPEN_AWAITING_PICKUP("Open - Awaiting pickup"),
  OPEN_IN_TRANSIT("Open - In transit"),
  OPEN_AWAITING_DELIVERY("Open - Awaiting delivery"),
  CLOSED_FILLED("Closed - Filled"),
  CLOSED_CANCELLED("Closed - Cancelled"),
  CLOSED_UNFILLED("Closed - Unfilled"),
  CLOSED_PICKUP_EXPIRED("Closed - Pickup expired");

  private static final EnumSet<RequestStatus> VALID_STATUSES =
    EnumSet.range(OPEN_NOT_YET_FILLED, CLOSED_PICKUP_EXPIRED);
  private static final EnumSet<RequestStatus> OPEN_STATUSES =
    EnumSet.range(OPEN_NOT_YET_FILLED, OPEN_AWAITING_DELIVERY);

  private final String value;

  public static String invalidStatusErrorMessage() {
    return "Request status must be one of the following: " +
      VALID_STATUSES.stream().map(s -> StringUtils.wrap(s.getValue(), '"'))
        .collect(Collectors.joining(", "));
  }

  public static RequestStatus from(String value) {
    return Arrays.stream(values())
      .filter(status -> status.valueMatches(value))
      .findFirst()
      .orElse(NONE);
  }

  public static RequestStatus from(JsonObject request) {
    return request.containsKey(RequestProperties.STATUS)
      ? from(request.getString(RequestProperties.STATUS))
      : OPEN_NOT_YET_FILLED;
  }

  RequestStatus(String value) {
    this.value = value;
  }

  static List<String> openStates() {
    return OPEN_STATUSES.stream().map(RequestStatus::getValue)
      .collect(Collectors.toList());
  }

  public boolean isValid() {
    return this != NONE;
  }

  public void writeTo(JsonObject request) {
    request.put(RequestProperties.STATUS, value);
  }

  private boolean valueMatches(String value) {
    return equalsIgnoreCase(getValue(), value);
  }

  public String getValue() {
    return value;
  }
}
