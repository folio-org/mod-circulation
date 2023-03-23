package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;

public class StoredRequestRepresentation {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject storedRequest(Request request) {
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
    addStoredInstanceProperties(representation, request.getInstance());
    addStoredRequesterProperties(representation, request.getRequester());
    addStoredProxyProperties(representation, request.getProxy());
    addSearchIndexProperties(representation, request, request.getItem());

    removeDeliveryAddress(representation);

    return representation;
  }

  private static void addStoredItemProperties(JsonObject request, Item item) {
    if (item == null || item.isNotFound()) {
      logUnableAddItemToTheRequest(request, item);
      return;
    }

    JsonObject itemSummary = new JsonObject();
    write(itemSummary, "barcode", item.getBarcode());

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
      String msg = "Unable to add requester properties to the request: {}, requester is null.";
      log.info(msg, request.getString("id"));
      return;
    }

    request.put("requester", storedUserSummary(requester));
  }

  private static void addStoredProxyProperties(JsonObject request, User proxy) {
    if (proxy == null) {
      String msg = "Unable to add proxy properties to request {}, proxy object is null";
      log.info(msg, request.getString("id"));
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

  private static void addSearchIndexProperties(JsonObject requestJson, Request request, Item item) {
    JsonObject searchIndex = new JsonObject();

    if (item != null && !item.isNotFound()) {
      CallNumberComponents callNumberComponents = item.getCallNumberComponents();
      if (callNumberComponents != null) {
        write(searchIndex, "callNumberComponents",
          createCallNumberComponents(callNumberComponents));
      } else {
        log.info("Unable to add callNumberComponents properties to the request: {}," +
          " callNumberComponents are null.", request.getId());
      }

      write(searchIndex, "shelvingOrder", item.getShelvingOrder());
    }

    ServicePoint pickupServicePoint = request.getPickupServicePoint();
    if (pickupServicePoint != null) {
      write(searchIndex, "pickupServicePointName", pickupServicePoint.getName());
    } else {
      log.info("Unable to add pickupServicePoint properties to the request: {}," +
        " pickupServicePoint is null.", request.getId());
    }

    requestJson.put("searchIndex", searchIndex);
  }

  private static void logUnableAddItemToTheRequest(JsonObject request, Item item) {
    String reason = isNull(item) ? "null" : "not found";
    String msg = "Unable to add item properties to the request: {}, item is {}";
    log.info(msg, request.getString("id"), reason);
  }

  private static void removeDeliveryAddress(JsonObject requestRepresentation) {
    if (requestRepresentation == null) {
      log.info("Unable to remove deliveryAddress, request representation is null");
      return;
    }
    requestRepresentation.remove("deliveryAddress");
  }
}

