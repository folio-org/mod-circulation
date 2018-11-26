package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.copyProperty;
import static org.folio.circulation.support.JsonPropertyWriter.write;

import java.lang.invoke.MethodHandles;

import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RequestRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  
  public JsonObject extendedRepresentation(Request request) {
    final JsonObject requestRepresentation = request.asJson();

    addAdditionalItemProperties(requestRepresentation, request.getItem());
    addAdditionalLoanProperties(requestRepresentation, request.getLoan());
    addAdditionalRequesterProperties(requestRepresentation, request.getRequester());
    addAdditionalProxyProperties(requestRepresentation, request.getProxy());
    addAdditionalServicePointProperties(requestRepresentation, request.getPickupServicePoint());
    addDeliveryAddress(requestRepresentation, request, request.getRequester());

    return requestRepresentation;
  }

  JsonObject storedRequest(Request request) {
    final JsonObject representation = request.asJson();

    addStoredItemProperties(representation, request.getItem());
    addStoredRequesterProperties(representation, request.getRequester());
    addStoredProxyProperties(representation, request.getProxy());

    removeDeliveryAddress(representation);

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

  private static void addAdditionalRequesterProperties(
    JsonObject requestWithAdditionalInformation,
    User requester) {

    if(requester == null) {
      return;
    }

    JsonObject requesterSummary = requester.createUserSummary();

    requestWithAdditionalInformation.put("requester", requesterSummary);

    String patronGroupId = requester.getPatronGroupId();
    if(patronGroupId != null) {
      requesterSummary.put("patronGroupId", patronGroupId);
    }
  }

  private static void addStoredProxyProperties(
    JsonObject requestWithAdditionalInformation,
    User proxy) {

    if(proxy == null) {
      log.info("Unable to add proxy properties to request {}, proxy object is null",
          requestWithAdditionalInformation.getString("id"));
      return;
    }

    requestWithAdditionalInformation.put("proxy", proxy.createUserSummary());
  }

  private static void addAdditionalProxyProperties(
    JsonObject requestWithAdditionalInformation,
    User proxy) {

    if(proxy == null) {
      log.info("Unable to add proxy properties to request {}, proxy object is null",
        requestWithAdditionalInformation.getString("id"));
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
      log.info("Unable to add item properties to request {}, item is null",
          request.getString("id"));
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

  private static void addDeliveryAddress(
    JsonObject requestWithAdditionalInformation,
    Request request,
    User requester) {

    if (requester == null) {
      return;
    }

    if (request == null) {
      return;
    }

    if (requestWithAdditionalInformation == null) {
      return;
    }

    String addressType = request.getDeliveryAddressType();
    if(addressType != null) {
      JsonObject address = requester.getAddressByType(addressType);
      if(address != null) {
        JsonObject mappedAddress = new JsonObject();

        copyProperty(address, mappedAddress, "addressTypeId");
        copyProperty(address, mappedAddress, "addressLine1");
        copyProperty(address, mappedAddress, "addressLine2");
        copyProperty(address, mappedAddress, "city");
        copyProperty(address, mappedAddress, "region");
        copyProperty(address, mappedAddress, "postalCode");
        copyProperty(address, mappedAddress, "countryId");

        requestWithAdditionalInformation.put("deliveryAddress", mappedAddress);
      }
    }
  }

  private static void removeDeliveryAddress(JsonObject requestRepresentation) {
    if (requestRepresentation == null) {
      return;
    }
    requestRepresentation.remove("deliveryAddress");
  }

  private static void addAdditionalLoanProperties(JsonObject request, Loan loan) {
    if(loan == null || loan.isClosed()) {
      String reason = null;
      if(loan == null) { reason = "null"; } else { reason = "closed"; }
      log.info("Unable to add loan properties to request {}, loan is {}",
          request.getString("id"), reason);
      return;
    }
    JsonObject loanSummary = request.containsKey("loan")
      ? request.getJsonObject("loan")
      : new JsonObject();
    
    if(loan.getDueDate() != null) {
      String dueDate = loan.getDueDate().toString(ISODateTimeFormat.dateTime());
      loanSummary.put("dueDate", dueDate);
      log.info("Adding loan properties to request {}", request.getString("id"));
    }
    
    request.put("loan", loanSummary);
  }
  
  private static void addAdditionalServicePointProperties(JsonObject request, ServicePoint servicePoint) {
    if(servicePoint == null) {
      log.info("Unable to add servicepoint properties to request {},"
          + " servicepoint is null", request.getString("id"));
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

