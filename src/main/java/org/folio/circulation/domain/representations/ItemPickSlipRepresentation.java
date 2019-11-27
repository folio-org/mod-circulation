package org.folio.circulation.domain.representations;

import static org.folio.circulation.support.JsonPropertyWriter.write;

import io.vertx.core.json.JsonObject;

import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Location;

public class ItemPickSlipRepresentation {

  public JsonObject create(Item item) {
    if(item == null || item.isNotFound()) {
      return new JsonObject();
    }

    JsonObject itemSummary = new JsonObject();

    write(itemSummary, "id", item.getItemId());
    write(itemSummary, "holdingsRecordId", item.getHoldingsRecordId());
    write(itemSummary, "instanceId", item.getInstanceId());
    write(itemSummary, "title", item.getTitle());
    write(itemSummary, "barcode", item.getBarcode());
    write(itemSummary, "callNumber", item.getCallNumber());
    write(itemSummary, "status", item.getStatus().getValue());
    write(itemSummary, "contributors", item.getContributorNames());

    final Location location = item.getLocation();
    if(location != null) {
      itemSummary.put("location", new JsonObject()
        .put("name", location.getName())
        .put("code", location.getCode())
      );
    }

    return itemSummary;
  }

}
