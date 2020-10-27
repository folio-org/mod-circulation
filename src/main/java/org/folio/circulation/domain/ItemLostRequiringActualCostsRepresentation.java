package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;

public class ItemLostRequiringActualCostsRepresentation {

  public JsonObject mapToResult(ItemLostRequiringCostsEntry entry) {
    if(entry == null) {
      return null;
    }

    JsonObject representation = entry.getItem().getItem();

    if (entry.getLoan() != null) {
      representation.put("loan", entry.getLoan().asJson());
    }

    return representation;
  }
}
