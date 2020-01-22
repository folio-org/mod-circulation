package org.folio.circulation.domain;

import io.vertx.core.json.JsonObject;
import org.folio.circulation.support.JsonPropertyWriter;

public class AccountRepresentation extends JsonObject {

  public AccountRepresentation(Loan loan, Item item, FeeFineOwner feeFineOwner,
                               FeeFine feeFine, Double amount) {
    super();
    JsonPropertyWriter.write(this, "ownerId", feeFineOwner.getId());
    JsonPropertyWriter.write(this, "feeFineId", feeFine.getId());
    JsonPropertyWriter.write(this, "amount", amount);
    JsonPropertyWriter.write(this, "remaining", amount);
    JsonPropertyWriter.write(this, "feeFineType", feeFine.getFeeFineType());
    JsonPropertyWriter.write(this, "feeFineOwner", feeFineOwner.getOwner());
    JsonPropertyWriter.write(this, "title", item.getTitle());
    JsonPropertyWriter.write(this, "barcode", item.getBarcode());
    JsonPropertyWriter.write(this, "callNumber", item.getCallNumber());
    JsonPropertyWriter.write(this, "location", item.getLocationId());
    JsonPropertyWriter.write(this, "materialTypeId", item.getMaterialTypeId());
    JsonPropertyWriter.write(this, "loanId", loan.getId());
    JsonPropertyWriter.write(this, "userId", loan.getUserId());
    JsonPropertyWriter.write(this, "itemId", item.getItemId());

    JsonObject paymentStatus = new JsonObject();
    JsonPropertyWriter.write(paymentStatus, "name", "Outstanding");
    JsonPropertyWriter.write(this, "paymentStatus", paymentStatus);

    JsonObject status = new JsonObject();
    JsonPropertyWriter.write(status, "name", "Open");
    JsonPropertyWriter.write(this, "status", status);
  }
}
