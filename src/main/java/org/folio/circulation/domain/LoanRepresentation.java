package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.InventoryRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.JsonPropertyWriter.write;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(LoanAndRelatedRecords relatedRecords) {
    return extendedLoan(relatedRecords.getLoan().asJson(),
      relatedRecords.getLoan().getInventoryRecords(),
      relatedRecords.getLoan().getInventoryRecords().item,
      relatedRecords.getLoan().getInventoryRecords().holding,
      relatedRecords.getLocation(),
      relatedRecords.getMaterialType());
  }

  public JsonObject createItemSummary(
    JsonObject item,
    JsonObject holding,
    JsonObject location,
    JsonObject materialType,
    InventoryRecords inventoryRecords) {
    JsonObject itemSummary = new JsonObject();

    final String barcodeProperty = "barcode";
    final String statusProperty = "status";
    final String holdingsRecordIdProperty = "holdingsRecordId";
    final String instanceIdProperty = "instanceId";
    final String callNumberProperty = "callNumber";
    final String materialTypeProperty = "materialType";

    write(itemSummary, "title", inventoryRecords.getTitle());

    write(itemSummary, "contributors", inventoryRecords.getContributorNames());

    if(item.containsKey(barcodeProperty)) {
      itemSummary.put(barcodeProperty, item.getString(barcodeProperty));
    }

    if(item.containsKey(holdingsRecordIdProperty)) {
      itemSummary.put(holdingsRecordIdProperty, item.getString(holdingsRecordIdProperty));
    }

    if(holding != null && holding.containsKey(instanceIdProperty)) {
      itemSummary.put(instanceIdProperty, holding.getString(instanceIdProperty));
    }

    if(holding != null && holding.containsKey(callNumberProperty)) {
      itemSummary.put(callNumberProperty, holding.getString(callNumberProperty));
    }

    if(item.containsKey(statusProperty)) {
      itemSummary.put(statusProperty, item.getJsonObject(statusProperty));
    }

    if(location != null && location.containsKey("name")) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getString("name")));
    }

    if(materialType != null) {
      if(materialType.containsKey("name") && materialType.getString("name") != null) {
        itemSummary.put(materialTypeProperty, new JsonObject()
          .put("name", materialType.getString("name")));
      } else {
        log.warn("Missing or null property for material type for item id {}",
          item.getString("id"));
      }
    } else {
      log.warn("Null materialType object for item {}", item.getString("id"));
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(
    JsonObject loan,
    InventoryRecords inventoryRecords,
    JsonObject item,
    JsonObject holding,
    JsonObject location,
    JsonObject materialType) {

    if(item != null) {
      loan.put("item", new LoanRepresentation().createItemSummary(item, holding, location,
        materialType, inventoryRecords));
    }

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    return loan;
  }
}
