package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.representations.RequestProperties;

import java.util.Arrays;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public enum RequestStatus {
  NONE(""),
  OPEN_NOT_YET_FILLED("Open - Not yet filled"),
  OPEN_AWAITING_PICKUP("Open - Awaiting pickup"),
  CLOSED_FILLED("Closed - Filled"),
  CLOSED_CANCELLED("Closed - Cancelled");

  private final String name;

  public static String invalidStatusErrorMessage() {
    //TODO: Generalise this to join all states
    return String.format("Request status must be \"%s\", \"%s\" or \"%s\"",
      OPEN_NOT_YET_FILLED.getName(), OPEN_AWAITING_PICKUP.getName(),
      CLOSED_FILLED.getName());
  }

  public static RequestStatus from(String name) {
    return Arrays.stream(values())
      .filter(status -> status.nameMatches(name))
      .findFirst()
      .orElse(NONE);
  }

  public static RequestStatus from(JsonObject request) {
    return request.containsKey(RequestProperties.STATUS)
      ? from(request.getString(RequestProperties.STATUS))
      : OPEN_NOT_YET_FILLED;
  }

  RequestStatus(String name) {
    this.name = name;
  }

  public boolean isValid() {
    return this != NONE;
  }

  public void writeTo(JsonObject request) {
    request.put(RequestProperties.STATUS, name);
  }

  private boolean nameMatches(String name) {
    return equalsIgnoreCase(getName(), name);
  }

  public String getName() {
    return name;
  }
}
