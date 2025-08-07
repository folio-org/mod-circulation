package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.circulation.domain.representations.ItemProperties;

import io.vertx.core.json.JsonObject;

public class StoredRequestRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject storedRequest(Request request) {
    log.debug("storedRequest:: parameters request: {}", () -> request);
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
    addStoredInstanceProperties(representation, request.getInstance());
    addStoredRequesterProperties(representation, request.getRequester());
    addStoredProxyProperties(representation, request.getProxy());
    addSearchIndexProperties(representation, request);

    removeDeliveryAddress(representation);

    return representation;
  }

  private static void addStoredItemProperties(JsonObject request, Item item) {
    log.debug("addStoredItemProperties:: parameters request: {}, item: {}",
      () -> request, () -> item);
    if (item == null || item.isNotFound()) {
      logUnableAddItemToTheRequest(request, item);
      return;
    }

    JsonObject itemSummary = new JsonObject();
    write(itemSummary, "barcode", item.getBarcode());

    if (Objects.nonNull(item.getLocation())) {
      write(itemSummary, "itemEffectiveLocationId", item.getLocation().getId());
      write(itemSummary, "itemEffectiveLocationName",
        item.getLocation().getName());

      if (Objects.nonNull(item.getLocation().getPrimaryServicePoint())) {
        write(itemSummary, "retrievalServicePointId",
          item.getLocation().getPrimaryServicePoint().getId());
        write(itemSummary, "retrievalServicePointName",
          item.getLocation().getPrimaryServicePoint().getName());
      }
    }
    write(itemSummary, "loanTypeId", item.getLoanTypeId());
    write(itemSummary, "loanTypeName", item.getLoanTypeName());

    request.put("item", itemSummary);
  }

  private static void addStoredInstanceProperties(JsonObject request, Instance instance) {
    if (instance == null || instance.isNotFound()) {
      log.info("Unable to add instance properties to request {}, instance is {}",
        request.getString("id"), request.getString("instanceId"));
      return;
    }
    JsonObject instanceSummary = new JsonObject();
    write(instanceSummary, "title", instance.getTitle());
    write(instanceSummary, "identifiers", identifiersToJson(instance.getIdentifiers()));

    request.put("instance", instanceSummary);
  }

  private static Collection<JsonObject> identifiersToJson(Collection<Identifier> identifiers) {
    return identifiers.stream()
      .map(StoredRequestRepresentation::identifierToJson)
      .collect(toList());
  }

  private static JsonObject identifierToJson(Identifier identifier) {
    final var representation = new JsonObject();

    write(representation, "identifierTypeId", identifier.getIdentifierTypeId());
    write(representation, "value", identifier.getValue());

    return representation;
  }

  private static void addStoredRequesterProperties(JsonObject request, User requester) {
    if (requester == null) {
      logUnableToAddNullPropertiesToTheRequest("requester", request);
      return;
    }

    request.put("requester", storedUserSummary(requester));
  }

  private static void addStoredProxyProperties(JsonObject request, User proxy) {
    if (proxy == null) {
      logUnableToAddNullPropertiesToTheRequest("proxy", request);
      return;
    }

    request.put("proxy", storedUserSummary(proxy));
  }

  private static JsonObject storedUserSummary(User user) {
    JsonObject userSummary = new JsonObject();

    write(userSummary, "lastName", user.getLastName());
    write(userSummary, "firstName", user.getFirstName());
    write(userSummary, "middleName", user.getMiddleName());
    write(userSummary, "barcode", user.getBarcode());

    return userSummary;
  }

  private static void addSearchIndexProperties(JsonObject requestJson, Request request) {
    log.debug("addSearchIndexProperties:: parameters requestJson: {}, request: {}",
      () -> requestJson, () -> request);
    JsonObject searchIndex = new JsonObject();

    if (request.hasItem()) {
      Item item = request.getItem();
      CallNumberComponents callNumberComponents = item.getCallNumberComponents();
      if (callNumberComponents != null) {
        write(searchIndex, ItemProperties.CALL_NUMBER_COMPONENTS,
          createCallNumberComponents(callNumberComponents));
      } else {
        logUnableToAddNullPropertiesToTheRequest(ItemProperties.CALL_NUMBER_COMPONENTS,
          requestJson);
      }

      write(searchIndex, ItemProperties.SHELVING_ORDER, item.getShelvingOrder());
    }

    ServicePoint pickupServicePoint = request.getPickupServicePoint();
    if (pickupServicePoint != null) {
      write(searchIndex, "pickupServicePointName", pickupServicePoint.getName());
    } else {
      logUnableToAddNullPropertiesToTheRequest("pickupServicePoint", requestJson);
    }

    requestJson.put("searchIndex", searchIndex);
  }

  private static void logUnableAddItemToTheRequest(JsonObject request, Item item) {
    String reason = isNull(item) ? "null" : "not found";
    String msg = "Unable to add item properties to the request: {}, item is {}";
    log.info(msg, request.getString("id"), reason);
  }

  private static void logUnableToAddNullPropertiesToTheRequest(String fieldName,
    JsonObject requestRepresentation) {

    log.info("Unable to add {} properties to the request: {}," +
      " {} is null.", fieldName, requestRepresentation.getString("id"), fieldName);
  }

  private static void removeDeliveryAddress(JsonObject requestRepresentation) {
    if (requestRepresentation == null) {
      log.info("Unable to remove deliveryAddress, request representation is null");
      return;
    }
    requestRepresentation.remove("deliveryAddress");
  }
}

