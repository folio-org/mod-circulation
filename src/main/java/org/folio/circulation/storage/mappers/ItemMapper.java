package org.folio.circulation.storage.mappers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.LastCheckIn;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class ItemMapper {
  public Item toDomain(JsonObject representation) {
    return new Item(representation, null,
      LastCheckIn.fromItemJson(representation),
      CallNumberComponents.fromItemJson(representation), null,
      getInTransitServicePoint(representation), false,
      Holdings.unknown(getProperty(representation, "holdingsRecordId")),
      Instance.unknown(),
      MaterialType.unknown(),
      LoanType.unknown(),
      getProperty(representation, "barcode"));
  }

  private ServicePoint getInTransitServicePoint(JsonObject representation) {
    final var inTransitDestinationServicePointId
      = getProperty(representation, "inTransitDestinationServicePointId");

    if (inTransitDestinationServicePointId == null) {
      return null;
    }
    else {
      return ServicePoint.unknown(inTransitDestinationServicePointId);
    }
  }


}
