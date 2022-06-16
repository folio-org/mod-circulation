package org.folio.circulation.storage.mappers;

import org.folio.circulation.domain.ActualCostRecord;
import org.folio.circulation.domain.LossType;

import io.vertx.core.json.JsonObject;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;
import static org.folio.circulation.support.json.JsonPropertyWriter.write;

public class ActualCostRecordMapper {

  public JsonObject toJson(ActualCostRecord actualCostRecord) {
    JsonObject json = new JsonObject();
    write(json, "id", actualCostRecord.getId());
    write(json, "userId", actualCostRecord.getUserId());
    write(json, "userBarcode", actualCostRecord.getUserBarcode());
    write(json, "loanId", actualCostRecord.getLoanId());
    write(json, "lossType", actualCostRecord.getLossType().getValue());
    write(json, "dateOfLoss", actualCostRecord.getDateOfLoss());
    write(json, "title", actualCostRecord.getTitle());
    write(json, "identifiers", actualCostRecord.getIdentifiers());
    write(json, "itemBarcode", actualCostRecord.getItemBarcode());
    write(json, "loanType", actualCostRecord.getLoanType());
    write(json, "effectiveCallNumber", actualCostRecord.getEffectiveCallNumber());
    write(json, "permanentItemLocation", actualCostRecord.getPermanentItemLocation());
    write(json, "feeFineOwnerId", actualCostRecord.getFeeFineOwnerId());
    write(json, "feeFineOwner", actualCostRecord.getFeeFineOwner());
    write(json, "feeFineTypeId", actualCostRecord.getFeeFineTypeId());
    write(json, "feeFineType", actualCostRecord.getFeeFineType());

    return json;
  }

  public ActualCostRecord toDomain(JsonObject representation) {
    return new ActualCostRecord(getProperty(representation, "id"),
      getProperty(representation, "userId"),
      getProperty(representation, "userBarcode"),
      getProperty(representation, "loanId"),
      LossType.from(getProperty(representation, "lossType")),
      getProperty(representation, "dateOfLoss"),
      getProperty(representation, "title"),
      IdentifierMapper.mapIdentifiers(representation),
      getProperty(representation, "itemBarcode"),
      getProperty(representation, "loanType"),
      getProperty(representation, "effectiveCallNumber"),
      getProperty(representation, "permanentItemLocation"),
      getProperty(representation, "feeFineOwnerId"),
      getProperty(representation, "feeFineOwner"),
      getProperty(representation, "feeFineTypeId"),
      getProperty(representation, "feeFineType")
      );
  }
}
