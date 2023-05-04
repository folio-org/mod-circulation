package org.folio.circulation.domain.representations.logs;

import static java.util.Optional.ofNullable;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.DESTINATION_SERVICE_POINT;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_BARCODE;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.ITEM_STATUS_NAME;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.REQUESTS;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SERVICE_POINT_ID;
import static org.folio.circulation.domain.representations.logs.LogEventPayloadField.SOURCE;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.LogUtil.listAsString;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.Request;
import org.folio.circulation.domain.UpdatedRequestPair;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestUpdateLogEventMapper {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private RequestUpdateLogEventMapper() {}

  public static JsonObject mapToRequestLogEventJson(Request request) {
    log.debug("mapToRequestLogEventJson:: parameters request: {}", request);

    JsonObject logEventPayload = new JsonObject();
    write(logEventPayload, SERVICE_POINT_ID.value(), request.getItem()
      .getLastCheckInServicePointId());
    populateItemData(request, logEventPayload);
    write(logEventPayload, REQUESTS.value(), mapCreatedRequestToJson(request));

    log.info("mapToRequestLogEventJson:: result {}", logEventPayload::encode);
    return logEventPayload;
  }

  public static JsonObject mapToRequestLogEventJson(Request original, Request updated) {
    log.debug("mapToRequestLogEventJson:: parameters original: {}, updated: {}", original, updated);

    JsonObject logEventPayload = new JsonObject();
    write(logEventPayload, SERVICE_POINT_ID.value(), original.getItem()
      .getLastCheckInServicePointId());
    populateItemData(original, logEventPayload);
    write(logEventPayload, REQUESTS.value(), mapUpdatedRequestPairToJson(
      new UpdatedRequestPair(original, updated)));

    log.info("mapToRequestLogEventJson:: result {}", logEventPayload::encode);
    return logEventPayload;
  }

  public static JsonObject mapToRequestLogEventJson(List<Request> requests) {
    log.debug("mapToRequestLogEventJson:: parameters requests: {}", () -> listAsString(requests));

    JsonObject logEventPayload = new JsonObject();
    write(logEventPayload, SERVICE_POINT_ID.value(), requests.get(0)
      .getItem()
      .getLastCheckInServicePointId());
    populateItemData(requests.get(0), logEventPayload);
    write(logEventPayload, REQUESTS.value(), mapReorderedRequestsToJsonArray(requests));

    log.info("mapToRequestLogEventJson:: result {}", logEventPayload::encode);
    return logEventPayload;
  }

  private static void populateItemData(Request request, JsonObject logEventPayload) {
    log.debug("populateItemData:: parameters request: {}, logEventPayload: {}", request,
      logEventPayload);

    ofNullable(request.getItem()).ifPresent(item -> {
      write(logEventPayload, ITEM_ID.value(), item.getItemId());
      write(logEventPayload, ITEM_BARCODE.value(), item.getBarcode());
      write(logEventPayload, ITEM_STATUS_NAME.value(), item.getStatusName());
      ofNullable(item.getInTransitDestinationServicePoint())
        .ifPresent(sp -> write(logEventPayload, DESTINATION_SERVICE_POINT.value(), sp.getName()));
      write(logEventPayload, SOURCE.value(), item.getMaterialType().getSource());
    });
  }

  private static JsonObject mapUpdatedRequestPairToJson(UpdatedRequestPair updatedRequestPair) {
    log.debug("mapUpdatedRequestPairToJson:: parameters updatedRequestPair: {}", updatedRequestPair);

    JsonObject requestPayload = new JsonObject();
    ofNullable(updatedRequestPair.getOriginal()).ifPresent(original -> requestPayload.put("original", original.asJson()));
    ofNullable(updatedRequestPair.getUpdated()).ifPresent(updated -> requestPayload.put("updated", updated.asJson()));

    log.info("mapUpdatedRequestPairToJson:: result {}", requestPayload::encode);
    return requestPayload;
  }

  private static JsonObject mapCreatedRequestToJson(Request request) {
    log.debug("mapCreatedRequestToJson:: parameters request: {}", request);

    JsonObject requestPayload = new JsonObject();
    requestPayload.put("created", request.asJson());

    log.info("mapCreatedRequestToJson:: result {}", requestPayload::encode);
    return requestPayload;
  }

  private static JsonObject mapReorderedRequestsToJsonArray(List<Request> requests) {
    log.debug("mapReorderedRequestsToJsonArray:: parameters requests: {}",
      () -> listAsString(requests));

    JsonObject requestPayload = new JsonObject();
    requestPayload.put("reordered", new JsonArray(requests.stream()
      .map(r -> r.asJson().put("previousPosition", r.getPreviousPosition()))
      .collect(Collectors.toList())));

    log.info("mapReorderedRequestsToJsonArray:: result {}", requestPayload::encode);
    return requestPayload;
  }
}
