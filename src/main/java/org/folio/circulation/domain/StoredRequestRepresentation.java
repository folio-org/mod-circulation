package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;
import static org.folio.circulation.support.utils.RequestRepresentationUtil.entityCollectionToJson;

import java.lang.invoke.MethodHandles;

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
    write(instanceSummary, "identifiers", entityCollectionToJson(instance.getIdentifiers()));

    request.put("instance", instanceSummary);
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

