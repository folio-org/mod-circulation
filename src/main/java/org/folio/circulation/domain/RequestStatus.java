package org.folio.circulation.domain;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.folio.circulation.domain.representations.RequestProperties;

import io.vertx.core.json.JsonObject;

public enum RequestStatus {
  NONE(""),
  OPEN_NOT_YET_FILLED("Open - Not yet filled"),
  OPEN_AWAITING_PICKUP("Open - Awaiting pickup"),
  OPEN_IN_TRANSIT("Open - In transit"),
  CLOSED_FILLED("Closed - Filled"),
  CLOSED_CANCELLED("Closed - Cancelled"),
  CLOSED_UNFILLED("Closed - Unfilled"),
  CLOSED_PICKUP_EXPIRED("Closed - Pickup expired");

  private final String value;

  public static String invalidStatusErrorMessage() {
    //TODO: Generalise this to join all states
    return String.format("Request status must be \"%s\", \"%s\", \"%s\", \"%s\", \"%s\" or \"%s\"",
      OPEN_NOT_YET_FILLED.getValue(), OPEN_AWAITING_PICKUP.getValue(),
      OPEN_IN_TRANSIT.getValue(), CLOSED_FILLED.getValue(),
      CLOSED_UNFILLED.getValue(), CLOSED_PICKUP_EXPIRED.getValue());
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

  public static List<String> openStates() {
    final ArrayList<String> openStates = new ArrayList<>();

    openStates.add(OPEN_AWAITING_PICKUP.getValue());
    openStates.add(OPEN_NOT_YET_FILLED.getValue());
    openStates.add(OPEN_IN_TRANSIT.getValue());

    return openStates;
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
