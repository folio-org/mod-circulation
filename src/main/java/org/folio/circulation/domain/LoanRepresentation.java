package org.folio.circulation.domain;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.representations.ItemSummaryRepresentation;
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

  private JsonObject extendedLoan(JsonObject loan, Item item) {
    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if(item != null && item.isFound()) {
      loan.put("item", new ItemSummaryRepresentation()
        .createItemSummary(item));
    }

    return loan;
  }
  
  private static void addAdditionalServicePointProperties(
    JsonObject loanRepresentation,
    ServicePoint servicePoint,
    String fieldName) {

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
