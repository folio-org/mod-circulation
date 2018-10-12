package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;


import static org.folio.circulation.support.JsonPropertyWriter.write;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class RequestRepresentation {
  public JsonObject extendedRepresentation(Request request) {
    final JsonObject requestRepresentation = request.asJson();

    addAdditionalItemProperties(requestRepresentation, request.getItem());
    addAdditionalLoanProperties(requestRepresentation, request.getLoan());
    addStoredProxyProperties(requestRepresentation, request.getProxy());

    return requestRepresentation;
  }

  JsonObject storedRequest(Request request) {
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
    addStoredRequesterProperties(representation, request.getRequester(), request);
    addStoredProxyProperties(representation, request.getProxy());

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
    User requester, Request request) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = requester.createUserSummary();

    requestWithAdditionalInformation.put("requester", requesterSummary);
    
    String addressType = request.getDeliveryAddressType();
    if(addressType != null) {
      JsonObject deliveryAddress = requester.getAddressByType(addressType);
      if(deliveryAddress != null) {
        requestWithAdditionalInformation.put("deliveryAddress", deliveryAddress);
      }
    }
    
    String patronGroup = requester.getPatronGroup();
    if(patronGroup != null) {
      requesterSummary.put("patronGroup", patronGroup);
    }
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
    
    JsonArray contributorNames = item.getContributorNames();
    if(contributorNames != null) {
      itemSummary.put("contributorNames", contributorNames);
    }
    
    String enumeration = item.getEnumeration();
    if(enumeration != null) {
      itemSummary.put("enumeration", enumeration);
    }
    
    ItemStatus status = item.getStatus();
    if(status != null) {
      String statusValue = status.getValue();
      itemSummary.put("status", statusValue);
    }
    
    String callNumber = item.getCallNumber();
    if(callNumber != null) {
      itemSummary.put("callNumber", callNumber);
    }
  }
  
  private static void addAdditionalLoanProperties(JsonObject request, Loan loan) {
    if(loan == null || loan.isClosed()) {
      return;
    }
    JsonObject loanSummary = request.containsKey("loan")
      ? request.getJsonObject("loan")
      : new JsonObject();
    
    if(loan.getDueDate() != null) {
      String dueDate = loan.getDueDate().toString(ISODateTimeFormat.dateTime());
      loanSummary.put("dueDate", dueDate);
    }
  }
}

