package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.domain.representations.RequestProperties;

import java.util.HashSet;

public class RequestStatus {
  public static final String OPEN_NOT_YET_FILLED = "Open - Not yet filled";
  public static final String OPEN_AWAITING_PICKUP = "Open - Awaiting pickup";
  public static final String CLOSED_FILLED = "Closed - Filled";

  public final String value;

  public static String invalidStatusErrorMessage() {
    return String.format("Request status must be \"%s\", \"%s\" or \"%s\"",
      OPEN_NOT_YET_FILLED, OPEN_AWAITING_PICKUP, CLOSED_FILLED);
  }

  public static RequestStatus from(JsonObject request) {
    String status = request.containsKey(RequestProperties.STATUS)
      ? request.getString(RequestProperties.STATUS)
      : OPEN_NOT_YET_FILLED;

    return new RequestStatus(status);
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

  public void writeTo(JsonObject request) {
    request.put(RequestProperties.STATUS, value);
  }
}
