package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class RequestRepresentation {
  public JsonObject extendedRepresentation(Request request) {
    final JsonObject requestRepresentation = request.asJson();

    addAdditionalItemProperties(requestRepresentation, request.getItem());

    return requestRepresentation;
  }

  JsonObject storedRequest(Request request, User proxy) {
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
    addStoredRequesterProperties(representation, request.getRequester());
    addStoredProxyProperties(representation, proxy);

    return representation;
  }

  private static void addStoredItemProperties(JsonObject request, Item item) {
    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());

    request.put("item", itemSummary);
  }

  private static void addStoredRequesterProperties(
    JsonObject requestWithAdditionalInformation,
    User requester) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = requester.createUserSummary();

    requestWithAdditionalInformation.put("requester", requesterSummary);
  }

  private static void addStoredProxyProperties(
    JsonObject requestWithAdditionalInformation,
    User proxy) {

    if(proxy == null) {
      return;
    }

    requestWithAdditionalInformation.put("proxy", proxy.createUserSummary());
  }

  private static void addAdditionalItemProperties(JsonObject request, Item item) {
    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = request.containsKey("item")
      ? request.getJsonObject("item")
      : new JsonObject();

    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());

    final JsonObject location = item.getLocation();

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    request.put("item", itemSummary);
  }
}
