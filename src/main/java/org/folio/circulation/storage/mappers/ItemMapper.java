package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LastCheckIn;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.MaterialType;

import io.vertx.core.json.JsonObject;

public class ItemMapper {
  public Item toDomain(JsonObject representation) {
    return new Item(representation,
      null,
      LastCheckIn.fromItemJson(representation),
      CallNumberComponents.fromItemJson(representation),
      null,
      null,
      false,
      Holdings.unknown(),
      Instance.unknown(),
      MaterialType.unknown(),
      LoanType.unknown());
  }
}
