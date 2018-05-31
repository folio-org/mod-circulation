package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(LoanAndRelatedRecords relatedRecords) {
    final InventoryRecords inventoryRecords = relatedRecords.getLoan().getInventoryRecords();

    //Temporary, until location and material type are included in inventory records
    inventoryRecords.setMaterialType(relatedRecords.getMaterialType());

    return extendedLoan(relatedRecords.getLoan().asJson(), inventoryRecords);
  }

  public JsonObject createItemSummary(InventoryRecords inventoryRecords) {

    if(inventoryRecords.isNotFound()) {
      return new JsonObject();
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "holdingsRecordId", inventoryRecords.getHoldingsRecordId());
    write(itemSummary, "instanceId", inventoryRecords.getInstanceId());
    write(itemSummary, "title", inventoryRecords.getTitle());
    write(itemSummary, "barcode", inventoryRecords.getBarcode());
    write(itemSummary, "contributors", inventoryRecords.getContributorNames());
    write(itemSummary, "callNumber", inventoryRecords.getCallNumber());
    writeNamedObject(itemSummary, "status", inventoryRecords.getStatus());

    final JsonObject location = inventoryRecords.getLocation();

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    final String materialTypeProperty = "materialType";

    final JsonObject materialType = inventoryRecords.getMaterialType();

    if(materialType != null) {
      if(materialType.containsKey("name") && materialType.getString("name") != null) {
        itemSummary.put(materialTypeProperty, new JsonObject()
          .put("name", materialType.getString("name")));
      } else {
        log.warn("Missing or null property for material type for item id {}",
          inventoryRecords.getItemId());
      }
    } else {
      log.warn("Null materialType object for item {}", inventoryRecords.getItemId());
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(
    JsonObject loan,
    InventoryRecords inventoryRecords) {

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if(inventoryRecords != null && inventoryRecords.isFound()) {
      loan.put("item", new LoanRepresentation().createItemSummary(inventoryRecords));
    }

    return loan;
  }
}
