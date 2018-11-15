package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.lang.invoke.MethodHandles;


import static org.folio.circulation.support.JsonPropertyWriter.write;

import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  public JsonObject extendedRepresentation(Request request) {
    final JsonObject requestRepresentation = request.asJson();

    addAdditionalItemProperties(requestRepresentation, request.getItem());
    addAdditionalLoanProperties(requestRepresentation, request.getLoan());
    addStoredProxyProperties(requestRepresentation, request.getProxy());
    addStoredRequesterProperties(requestRepresentation, request.getRequester(), request);
    addAdditionalServicePointProperties(requestRepresentation, request.getPickupServicePoint());

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
    
    String patronGroupId = requester.getPatronGroupId();
    if(patronGroupId != null) {
      requesterSummary.put("patronGroupId", patronGroupId);
    }
  }

  private static void addStoredProxyProperties(
    JsonObject requestWithAdditionalInformation,
    User proxy) {

    
    if(proxy == null) {
      log.info(String.format("Unable to add proxy properties to request %s, proxy object is null", 
          requestWithAdditionalInformation.getString("id")));
      return;
    }   

    JsonObject proxySummary =  proxy.createUserSummary();
    
    String patronGroupId = proxy.getPatronGroupId();
    if(patronGroupId != null) {
      proxySummary.put("patronGroupId", patronGroupId);
    }
    
    requestWithAdditionalInformation.put("proxy", proxySummary);
  }

  private static void addAdditionalItemProperties(JsonObject request, Item item) {
    if(item == null || item.isNotFound()) {
      log.info(String.format("Unable to add item properties to request %s, item is null",
          request.getString("id")));
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
      String reason = null;
      if(loan == null) { reason = "null"; } else { reason = "closed"; }
      log.info(String.format("Unable to add loan properties to request %s, loan is %s",
          request.getString("id"), reason));
      return;
    }
    JsonObject loanSummary = request.containsKey("loan")
      ? request.getJsonObject("loan")
      : new JsonObject();
    
    if(loan.getDueDate() != null) {
      String dueDate = loan.getDueDate().toString(ISODateTimeFormat.dateTime());
      loanSummary.put("dueDate", dueDate);
      log.info(String.format("Adding loan properties to request %s", 
          request.getString("id")));
    }
    
    request.put("loan", loanSummary);
  }
  
  private static void addAdditionalServicePointProperties(JsonObject request, ServicePoint servicePoint) {
    if(servicePoint == null) {
      log.info(String.format("Unable to add servicepoint properties to request %s,"
          + " servicepoint is null", request.getString("id")));
      return;
    }

    JsonObject spSummary = request.containsKey("pickupServicePoint")
        ? request.getJsonObject("pickupServicePoint")
        : new JsonObject();
    spSummary.put("name", servicePoint.getName());
    spSummary.put("code", servicePoint.getCode());
    spSummary.put("discoveryDisplayName", servicePoint.getDiscoveryDisplayName());
    spSummary.put("description", servicePoint.getDescription());
    spSummary.put("shelvingLagTime", servicePoint.getShelvingLagTime());
    spSummary.put("pickupLocation", servicePoint.getPickupLocation());    
    
    request.put("pickupServicePoint", spSummary);
  }
}

