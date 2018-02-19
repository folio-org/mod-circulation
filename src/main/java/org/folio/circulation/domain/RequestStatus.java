package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import java.util.HashSet;

public class RequestStatus {
  private static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  private static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  private static final String CLOSED_FILLED = "Closed - Filled";

  public final String value;

  public static String invalidStatusErrorMessage() {
    return String.format("Request status must be \"%s\", \"%s\" or \"%s\"",
      OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP, CLOSED_FILLED);
  }

  public static RequestStatus from(JsonObject request) {
    return new RequestStatus(request.getString("status"));
  }

  private RequestStatus(String value) {
    this.value = value;
  }

  public boolean isValid() {
    HashSet<String> allowedValues = new HashSet<>();

    allowedValues.add(OPEN_NOT_YET_FILLED);
    allowedValues.add(OPEN_AWAITING_PICKUP);
    allowedValues.add(CLOSED_FILLED);

    return allowedValues.contains(value);
  }
}
