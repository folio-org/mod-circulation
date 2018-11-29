package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;
import static org.folio.circulation.support.JsonPropertyWriter.writeNamedObject;

import java.lang.invoke.MethodHandles;

import org.folio.circulation.domain.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.core.json.JsonObject;

public class ItemSummaryRepresentation {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public JsonObject createItemSummary(Item item) {
    if(item == null || item.isNotFound()) {
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

    write(itemSummary, "inTransitDestinationServicePointId",
      item.getInTransitDestinationServicePointId());

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
}
