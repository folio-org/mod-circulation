package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.representations.RequestProperties;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public enum RequestStatus {
  NONE(""),
  OPEN_NOT_YET_FILLED("Open - Not yet filled"),
  OPEN_AWAITING_PICKUP("Open - Awaiting pickup"),
  OPEN_IN_TRANSIT("Open - In transit"),
  CLOSED_FILLED("Closed - Filled"),
  CLOSED_CANCELLED("Closed - Cancelled");

  private final String value;

  public static String invalidStatusErrorMessage() {
    //TODO: Generalise this to join all states
    return String.format("Request status must be \"%s\", \"%s\", \"%s\" or \"%s\"",
      OPEN_NOT_YET_FILLED.getValue(), OPEN_AWAITING_PICKUP.getValue(),
      OPEN_IN_TRANSIT.getValue(), CLOSED_FILLED.getValue());
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
