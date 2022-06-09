package org.folio.circulation.domain;

import static org.folio.circulation.support.json.JsonPropertyWriter.write;

import java.util.Collection;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.With;

@NoArgsConstructor
@AllArgsConstructor
@With
@Getter
public class ActualCostRecord {
  private String id;
  private String userId;
  private String userBarcode;
  private String loanId;
  private LossType lossType;
  private String dateOfLoss;
  private String title;
  private Collection<Identifier> identifiers;
  private String itemBarcode;
  private String loanType;
  private String effectiveCallNumber;
  private String permanentItemLocation;
  private String feeFineOwnerId;
  private String feeFineOwner;
  private String feeFineTypeId;
  private String feeFineType;

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    write(json, "id", id);
    write(json, "userId", userId);
    write(json, "userBarcode", userBarcode);
    write(json, "loanId", loanId);
    write(json, "lossType", lossType.getValue());
    write(json, "dateOfLoss", dateOfLoss);
    write(json, "title", title);
    write(json, "identifiers", identifiers);
    write(json, "itemBarcode", itemBarcode);
    write(json, "loanType", loanType);
    write(json, "effectiveCallNumber", effectiveCallNumber);
    write(json, "permanentItemLocation", permanentItemLocation);
    write(json, "feeFineOwnerId", feeFineOwnerId);
    write(json, "feeFineOwner", feeFineOwner);
    write(json, "feeFineTypeId", feeFineTypeId);
    write(json, "feeFineType", feeFineType);

    return json;
  }
}
