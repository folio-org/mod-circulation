package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class StoredRequestRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject storedRequest(Request request) {
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
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

    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());
    write(itemSummary, "identifiers", item.getIdentifiers());

    request.put("item", itemSummary);
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

