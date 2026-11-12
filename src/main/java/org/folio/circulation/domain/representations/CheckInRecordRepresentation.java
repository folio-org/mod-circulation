package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBooleanProperty;

import org.folio.circulation.domain.ServicePoint;
import org.folio.circulation.domain.User;

import io.vertx.core.json.JsonObject;

public class CheckInRecordRepresentation {
  private static final String IN_HOUSE_USE = "inHouseUse";
  private static final String OBJECT = "object";
  private static final String ACTION = "action";
  private static final String SOURCE = "source";
  private static final String SERVICE_POINT_NAME = "servicePointName";

  private static final String IN_HOUSE_USE_OBJECT = "In-house use";
  private static final String IN_HOUSE_USE_ACTION = "Recorded";
  private static final String REGULAR_OBJECT = "-";
  private static final String REGULAR_ACTION = "Checked in";

  public JsonObject withSource(JsonObject checkIn, User performedByUser) {
    return checkIn.copy().put(SOURCE,
      performedByUser == null ? null : performedByUser.getPersonalName());
  }

  public JsonObject withServicePointName(JsonObject checkIn, ServicePoint servicePoint) {
    return checkIn.copy().put(SERVICE_POINT_NAME,
      servicePoint == null ? null : servicePoint.getName());
  }

  public JsonObject withObjectAndAction(JsonObject checkIn) {
    final var enriched = checkIn.copy();

    if (getBooleanProperty(checkIn, IN_HOUSE_USE)) {
      return enriched.put(OBJECT, IN_HOUSE_USE_OBJECT).put(ACTION, IN_HOUSE_USE_ACTION);
    }

    return enriched.put(OBJECT, REGULAR_OBJECT).put(ACTION, REGULAR_ACTION);
  }
}
