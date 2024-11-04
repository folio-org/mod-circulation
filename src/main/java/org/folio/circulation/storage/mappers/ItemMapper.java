package org.folio.circulation.storage.mappers;

import static org.apache.commons.lang3.StringUtils.firstNonBlank;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonStringArrayPropertyFetcher.toStream;

import java.util.stream.Collectors;

import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.Holdings;
import org.folio.circulation.domain.Instance;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.ItemDescription;
import org.folio.circulation.domain.LastCheckIn;
import org.folio.circulation.domain.LoanType;
import org.folio.circulation.domain.Location;
import org.folio.circulation.domain.MaterialType;
import org.folio.circulation.domain.ServicePoint;

import io.vertx.core.json.JsonObject;

public class ItemMapper {

  public Item toDomain(JsonObject representation) {
    return new Item(getProperty(representation, "id"), representation,
      Location.unknown(getProperty(representation, "effectiveLocationId")),
      LastCheckIn.fromItemJson(representation),
      CallNumberComponents.fromItemJson(representation),
      getProperty(representation, "effectiveShelvingOrder"),
      Location.unknown(getProperty(representation, "permanentLocationId")),
      Location.unknown(),
      getInTransitServicePoint(representation), false,
      Holdings.unknown(getProperty(representation, "holdingsRecordId")),
      Instance.unknown(),
      MaterialType.unknown(getProperty(representation, "materialTypeId")),
      LoanType.unknown(getLoanTypeId(representation)), getDescription(representation));
  }

  private ItemDescription getDescription(JsonObject representation) {
    return new ItemDescription(
      getProperty(representation, "barcode"),
      getProperty(representation, "enumeration"),
      getProperty(representation, "copyNumber"),
      getProperty(representation, "volume"),
      getProperty(representation, "chronology"),
      getProperty(representation, "numberOfPieces"),
      getProperty(representation, "descriptionOfPieces"),
      getProperty(representation, "displaySummary"),
      toStream(representation, "yearCaption")
        .collect(Collectors.toList()));
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

  public String getLoanTypeId(JsonObject representation) {
    return firstNonBlank(
      getProperty(representation, "temporaryLoanTypeId"),
      getProperty(representation, "permanentLoanTypeId"));
  }
}
