package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.Item;

import io.vertx.core.json.JsonObject;

public class ItemMapper {
  public Item toDomain(JsonObject representation) {
    return Item.from(representation);
  }
}
