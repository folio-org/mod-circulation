package org.folio.circulation.domain;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
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
    if (item == null || item.isNotFound()) {
      logUnableAddItemToTheRequest(request, item);
      return;
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());

    request.put("item", itemSummary);
  }

  private static void addStoredRequesterProperties(JsonObject request, User requester) {

    if (requester == null) {
      String msg = "Unable to add requester properties to the request: {}, requester is null.";
      log.info(msg, request.getString("id"));
      return;
    }
    request.put("requester", requester.createUserSummary());
  }

  private static void addAdditionalRequesterProperties(JsonObject request, User requester) {

    if (requester == null) {
      String msg = "Unable to add requester properties to the request: {}, requester is null.";
      log.info(msg, request.getString("id"));
      return;
    }

    JsonObject requesterSummary = requester.createUserSummary();

    String patronGroupId = requester.getPatronGroupId();
    if (patronGroupId != null) {
      requesterSummary.put("patronGroupId", patronGroupId);
    }
    request.put("requester", requesterSummary);
  }

  private static void addStoredProxyProperties(JsonObject request, User proxy) {
    if (proxy == null) {
      String msg = "Unable to add proxy properties to request {}, proxy object is null";
      log.info(msg, request.getString("id"));
      return;
    }

    request.put("proxy", proxy.createUserSummary());
  }

  private static void addAdditionalProxyProperties(JsonObject request, User proxy) {

    if (proxy == null) {
      String msg = "Unable to add proxy properties to request {}, proxy object is null";
      log.info(msg, request.getString("id"));
      return;
    }

    JsonObject proxySummary = proxy.createUserSummary();
    request.put("proxy", proxySummary);

    String patronGroupId = proxy.getPatronGroupId();
    if (patronGroupId != null) {
      proxySummary.put("patronGroupId", patronGroupId);
    }

  }

  private static void addAdditionalItemProperties(JsonObject request, Item item) {
    if (item == null || item.isNotFound()) {
      logUnableAddItemToTheRequest(request, item);
      return;
    }

    JsonObject itemSummary = request.containsKey("item")
      ? request.getJsonObject("item")
      : new JsonObject();

    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());


    final Location location = item.getLocation();

    if (location != null) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getName()));
    }
    request.put("item", itemSummary);

    JsonArray contributorNames = item.getContributorNames();
    if (contributorNames != null) {
      itemSummary.put("contributorNames", contributorNames);
    }

    String enumeration = item.getEnumeration();
    if (enumeration != null) {
      itemSummary.put("enumeration", enumeration);
    }

    ItemStatus status = item.getStatus();
    if (status != null) {
      String statusValue = status.getValue();
      itemSummary.put("status", statusValue);
    }

    String callNumber = item.getCallNumber();
    if (callNumber != null) {
      itemSummary.put("callNumber", callNumber);
    }

    JsonArray copyNumbers = item.getCopyNumbers();
    if (copyNumbers != null) {
      itemSummary.put("copyNumbers", copyNumbers);
    }
  }

  private static void logUnableAddItemToTheRequest(JsonObject request, Item item) {
    String reason = isNull(item) ? "null" : "not found";
    String msg = "Unable to add item properties to the request: {}, item is {}";
    log.info(msg, request.getString("id"), reason);
  }

  private static void addDeliveryAddress(
    JsonObject requestRepresentation,
    Request request,
    User requester) {

    if (isNull(requestRepresentation)
      || isNull(request)
      || isNull(requester)) {
      log.info("Unable to add delivery address. Request is null or request is null or request representation is null.");
      return;
    }

    ofNullable(request.getDeliveryAddressType())
      .map(requester::getAddressByType)
      .ifPresent(address -> {
        JsonObject jsonObject = new JsonObject();

        copyProperty(address, jsonObject, "addressTypeId");
        copyProperty(address, jsonObject, "addressLine1");
        copyProperty(address, jsonObject, "addressLine2");
        copyProperty(address, jsonObject, "city");
        copyProperty(address, jsonObject, "region");
        copyProperty(address, jsonObject, "postalCode");
        copyProperty(address, jsonObject, "countryId");

        requestRepresentation.put("deliveryAddress", jsonObject);
      });
  }

  private static void removeDeliveryAddress(JsonObject requestRepresentation) {
    if (requestRepresentation == null) {
      log.info("Unable to remove deliveryAddress, request representation is null");
      return;
    }
    requestRepresentation.remove("deliveryAddress");
  }

  private static void addAdditionalLoanProperties(JsonObject request, Loan loan) {
    if (loan == null || loan.isClosed()) {
      String reason = isNull(loan) ? "null" : "closed";
      log.info("Unable to add loan properties to request {}, loan is {}", request.getString("id"), reason);
      return;
    }

    JsonObject loanSummary = request.containsKey("loan")
      ? request.getJsonObject("loan")
      : new JsonObject();

    if (loan.getDueDate() != null) {
      String dueDate = loan.getDueDate().toString(ISODateTimeFormat.dateTime());
      loanSummary.put("dueDate", dueDate);
      log.info("Adding loan properties to request {}", request.getString("id"));
    }

    request.put("loan", loanSummary);
  }

  private static void addAdditionalServicePointProperties(JsonObject request, ServicePoint servicePoint) {
    if (servicePoint == null) {
      String msg = "Unable to add servicepoint properties to request {}, servicepoint is null";
      log.info(msg, request.getString("id"));
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

