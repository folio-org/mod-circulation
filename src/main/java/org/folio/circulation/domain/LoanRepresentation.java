package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

import java.lang.invoke.MethodHandles;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(Loan loan) {
    if(loan == null) {
      return null;
    }

    JsonObject extendedRepresentation = extendedLoan(loan.asJson(), loan.getItem());
    
    if(loan.getCheckinServicePoint() != null) {
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckinServicePoint(), "checkinServicePoint");
    }
    
    if(loan.getCheckoutServicePoint() != null) {
      addAdditionalServicePointProperties(extendedRepresentation, loan.getCheckoutServicePoint(), "checkoutServicePoint");
    }
    
    return extendedRepresentation;
  }

  private JsonObject createItemSummary(Item item) {
    if(item.isNotFound()) {
      return new JsonObject();
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());
    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());
    write(itemSummary, "contributors", item.getContributorNames());
    write(itemSummary, "callNumber", item.getCallNumber());

    //TODO: Check for null item status
    writeNamedObject(itemSummary, "status", item.getStatus().getValue());

    final JsonObject location = item.getLocation();

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    final String materialTypeProperty = "materialType";

    final JsonObject materialType = item.getMaterialType();

    if(materialType != null) {
      if(materialType.containsKey("name") && materialType.getString("name") != null) {
        itemSummary.put(materialTypeProperty, new JsonObject()
          .put("name", materialType.getString("name")));
      } else {
        log.warn("Missing or null property for material type for item id {}",
          item.getItemId());
      }
    } else {
      log.warn("Null materialType object for item {}", item.getItemId());
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(JsonObject loan, Item item) {
    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if(item != null && item.isFound()) {
      loan.put("item", new LoanRepresentation().createItemSummary(item));
    }

    return loan;
  }
  
  private static void addAdditionalServicePointProperties(JsonObject loanRepresentation, ServicePoint servicePoint, String fieldName) {
    if(servicePoint == null) {
      log.info("Unable to add servicepoint properties to loan {},"
          + " servicepoint is null", loanRepresentation.getString("id"));
      return;
    }

    JsonObject spSummary = loanRepresentation.containsKey(fieldName)
        ? loanRepresentation.getJsonObject(fieldName)
        : new JsonObject();
    spSummary.put("name", servicePoint.getName());
    spSummary.put("code", servicePoint.getCode());
    spSummary.put("discoveryDisplayName", servicePoint.getDiscoveryDisplayName());
    spSummary.put("description", servicePoint.getDescription());
    spSummary.put("shelvingLagTime", servicePoint.getShelvingLagTime());
    spSummary.put("pickupLocation", servicePoint.getPickupLocation());    
    
    loanRepresentation.put(fieldName, spSummary);
  }
}
