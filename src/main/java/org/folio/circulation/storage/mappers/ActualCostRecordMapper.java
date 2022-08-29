package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.CallNumberComponents;
import org.folio.circulation.domain.ItemLossType;

import io.vertx.core.json.JsonObject;

import static org.folio.circulation.domain.representations.CallNumberComponentsRepresentation.createCallNumberComponents;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getNestedDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class ActualCostRecordMapper {

  private ActualCostRecordMapper() {
  }

  public static JsonObject toJson(ActualCostRecord actualCostRecord) {
    JsonObject json = new JsonObject();
    write(json, "id", actualCostRecord.getId());
    write(json, "userId", actualCostRecord.getUserId());
    write(json, "userBarcode", actualCostRecord.getUserBarcode());
    write(json, "loanId", actualCostRecord.getLoanId());
    write(json, "itemLossType", actualCostRecord.getItemLossType().getValue());
    write(json, "dateOfLoss", actualCostRecord.getDateOfLoss());
    write(json, "title", actualCostRecord.getTitle());
    write(json, "identifiers", actualCostRecord.getIdentifiers());
    write(json, "itemBarcode", actualCostRecord.getItemBarcode());
    write(json, "loanType", actualCostRecord.getLoanType());
    write(json, "effectiveCallNumberComponents",
      createCallNumberComponents(actualCostRecord.getCallNumberComponents()));
    write(json, "feeFineOwnerId", actualCostRecord.getFeeFineOwnerId());
    write(json, "feeFineOwner", actualCostRecord.getFeeFineOwner());
    write(json, "feeFineTypeId", actualCostRecord.getFeeFineTypeId());
    write(json, "feeFineType", actualCostRecord.getFeeFineType());
    write(json,"permanentItemLocation", actualCostRecord.getPermanentItemLocation());
    write(json, "expirationDate", actualCostRecord.getExpirationDate());

    if (actualCostRecord.getAccountId() != null) {
      write(json, "accountId", actualCostRecord.getAccountId());
    }

    return json;
  }

  public static ActualCostRecord toDomain(JsonObject representation) {
    if (representation == null ) {
      return null;
    }

    return new ActualCostRecord(getProperty(representation, "id"),
      getProperty(representation, "accountId"),
      getProperty(representation, "userId"),
      getProperty(representation, "userBarcode"),
      getProperty(representation, "loanId"),
      ItemLossType.from(getProperty(representation, "itemLossType")),
      getDateTimeProperty(representation, "dateOfLoss"),
      getProperty(representation, "title"),
      IdentifierMapper.mapIdentifiers(representation),
      getProperty(representation, "itemBarcode"),
      getProperty(representation, "loanType"),
      CallNumberComponents.fromItemJson(representation),
      getProperty(representation, "permanentItemLocation"),
      getProperty(representation, "feeFineOwnerId"),
      getProperty(representation, "feeFineOwner"),
      getProperty(representation, "feeFineTypeId"),
      getProperty(representation, "feeFineType"),
      getNestedDateTimeProperty(representation, "metadata", "createdDate"),
      getDateTimeProperty(representation, "expirationDate")
    );
  }
}
