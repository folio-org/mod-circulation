package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class RequestRepresentation {
  public JsonObject storedRequest(
    Request request,
    Item item,
    User requester,
    User proxy) {

    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, item);
    addStoredRequesterProperties(representation, requester);
    addStoredProxyProperties(representation, proxy);

    return representation;
  }

  private static void addStoredItemProperties(
    JsonObject request,
    Item item) {

    if(item == null || item.isNotFound()) {
      return;
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());

    request.put("item", itemSummary);
  }

  private static void addStoredRequesterProperties
    (JsonObject requestWithAdditionalInformation,
     User requester) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = requester.createUserSummary();

    requestWithAdditionalInformation.put("requester", requesterSummary);
  }

  private static void addStoredProxyProperties
    (JsonObject requestWithAdditionalInformation,
     User proxy) {

    if(proxy == null) {
      return;
    }

    requestWithAdditionalInformation.put("proxy", proxy.createUserSummary());
  }
}
