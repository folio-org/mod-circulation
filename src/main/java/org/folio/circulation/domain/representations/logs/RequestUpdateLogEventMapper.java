package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DESTINATION_SERVICE_POINT;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_STATUS_NAME;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.LOG_EVENT_TYPE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQUESTS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SOURCE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadType.REQUEST_CREATED;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadType.REQUEST_MOVED;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadType.REQUEST_UPDATED;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.UpdatedRequestPair;

import io.vertx.core.json.JsonObject;

public class RequestUpdateLogEventMapper {
  public static final String ITEM_SOURCE = "source";

  private RequestUpdateLogEventMapper() {
  }

  public static JsonObject mapToRequestCreatedLogEventJson(Request request) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), REQUEST_CREATED.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), request.getItem()
      .getLastCheckInServicePointId());

    populateItemData(request, logEventPayload);

    write(logEventPayload, REQUESTS.value(), mapCreatedRequestToJson(request));

    return logEventPayload;
  }

  public static JsonObject mapToRequestUpdateLogEventJson(UpdatedRequestPair pair) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), REQUEST_UPDATED.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), pair.getOriginal()
      .getItem()
      .getLastCheckInServicePointId());

    populateItemData(pair.getOriginal(), logEventPayload);

    write(logEventPayload, REQUESTS.value(), mapUpdatedRequestPairToJson(pair));

    return logEventPayload;
  }

  public static JsonObject mapToRequestMoveLogEventJson(UpdatedRequestPair pair) {
    JsonObject logEventPayload = new JsonObject();

    write(logEventPayload, LOG_EVENT_TYPE.value(), REQUEST_MOVED.value());
    write(logEventPayload, SERVICE_POINT_ID.value(), pair.getOriginal()
      .getItem()
      .getLastCheckInServicePointId());

    populateItemData(pair.getOriginal(), logEventPayload);

    write(logEventPayload, REQUESTS.value(), mapUpdatedRequestPairToJson(pair));

    return logEventPayload;
  }

  private static void populateItemData(Request request, JsonObject logEventPayload) {
    ofNullable(request.getItem()).ifPresent(item -> {
      write(logEventPayload, ITEM_ID.value(), item.getItemId());
      write(logEventPayload, ITEM_BARCODE.value(), item.getBarcode());
      write(logEventPayload, ITEM_STATUS_NAME.value(), item.getStatusName());
      ofNullable(item.getInTransitDestinationServicePoint())
        .ifPresent(sp -> write(logEventPayload, DESTINATION_SERVICE_POINT.value(), sp.getName()));
      ofNullable(item.getMaterialType()).ifPresent(mt -> write(logEventPayload, SOURCE.value(), mt.getString(ITEM_SOURCE)));
    });
  }

  private static JsonObject mapUpdatedRequestPairToJson(UpdatedRequestPair updatedRequestPair) {
    JsonObject requestPayload = new JsonObject();
    ofNullable(updatedRequestPair.getOriginal()).ifPresent(original -> requestPayload.put("original", original.asJson()));
    ofNullable(updatedRequestPair.getUpdated()).ifPresent(updated -> requestPayload.put("updated", updated.asJson()));
    return requestPayload;
  }

  private static JsonObject mapCreatedRequestToJson(Request request) {
    JsonObject requestPayload = new JsonObject();
    requestPayload.put("created", request.asJson());
    return requestPayload;
  }
}
