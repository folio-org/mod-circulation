package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

public class LoanRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject extendedLoan(Loan loan) {
    return extendedLoan(loan.asJson(), loan.getItem());
  }

  public JsonObject createItemSummary(Item item) {
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
    writeNamedObject(itemSummary, "status", item.getStatus());

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

  private JsonObject extendedLoan(
    JsonObject loan,
    Item item) {

    //No need to pass on the itemStatus property, as only used to populate the history
    //and could be confused with aggregation of current status
    loan.remove("itemStatus");

    if(item != null && item.isFound()) {
      loan.put("item", new LoanRepresentation().createItemSummary(item));
    }

    return loan;
  }
}
