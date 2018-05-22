package org.folio.circulation.domain;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private static final String CONTRIBUTORS_PROPERTY = "contributors";

  public JsonObject extendedLoan(LoanAndRelatedRecords relatedRecords) {
    return extendedLoan(relatedRecords.loan.asJson(),
      relatedRecords.inventoryRecords.item,
      relatedRecords.inventoryRecords.holding,
      relatedRecords.inventoryRecords.instance,
      relatedRecords.location,
      relatedRecords.materialType);
  }

  public JsonObject createItemSummary(
    JsonObject item,
    JsonObject instance,
    JsonObject holding,
    JsonObject location,
    JsonObject materialType) {
    JsonObject itemSummary = new JsonObject();

    final String titleProperty = "title";
    final String barcodeProperty = "barcode";
    final String statusProperty = "status";
    final String holdingsRecordIdProperty = "holdingsRecordId";
    final String instanceIdProperty = "instanceId";
    final String callNumberProperty = "callNumber";
    final String materialTypeProperty = "materialType";

    if(instance != null && instance.containsKey(titleProperty)) {
      itemSummary.put(titleProperty, instance.getString(titleProperty));
    } else if (item.containsKey("title")) {
      itemSummary.put(titleProperty, item.getString(titleProperty));
    }

    if(instance != null && instance.containsKey(CONTRIBUTORS_PROPERTY)) {
      JsonArray instanceContributors = instance.getJsonArray(CONTRIBUTORS_PROPERTY);

      if(instanceContributors != null && !instanceContributors.isEmpty()) {
        JsonArray contributors = new JsonArray();
        for(Object ob : instanceContributors) {
          String name = ((JsonObject)ob).getString("name");
          contributors.add(new JsonObject().put("name", name));
        }
        itemSummary.put(CONTRIBUTORS_PROPERTY, contributors);
      }
    }

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
        log.warn("Missing or null property for material type for item id " +
          item.getString("id"));
      }
    } else {
      log.warn(String.format("Null materialType object for item %s",
        item.getString("id")));
    }

    return itemSummary;
  }

  private JsonObject extendedLoan(
    JsonObject loan,
    JsonObject item,
    JsonObject holding,
    JsonObject instance,
    JsonObject location,
    JsonObject materialType) {

    if(item != null) {
      loan.put("item", new LoanRepresentation().createItemSummary(item, instance, holding, location,
        materialType));
    }

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    return loan;
  }
}
