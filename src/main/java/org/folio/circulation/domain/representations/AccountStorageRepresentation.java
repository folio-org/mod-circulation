package org.folio.circulation.domain.representations;

import java.util.UUID;

import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.Item;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.support.JsonPropertyWriter;

import io.vertx.core.json.JsonObject;

public class AccountStorageRepresentation extends JsonObject {
  public AccountStorageRepresentation(Loan loan, Item item, FeeFineOwner feeFineOwner,
    FeeFine feeFine, Double amount) {
    super();

    this.put("id", UUID.randomUUID().toString());
    this.put("ownerId", feeFineOwner.getId());
    this.put("feeFineId", feeFine.getId());
    this.put("amount", amount);
    this.put("remaining", amount);
    this.put("feeFineType", feeFine.getFeeFineType());
    this.put("feeFineOwner", feeFineOwner.getOwner());
    this.put("title", item.getTitle());
    this.put("barcode", item.getBarcode());
    this.put("callNumber", item.getCallNumber());
    this.put("location", item.getLocation().getPrimaryServicePointId().toString());
    this.put("materialTypeId", item.getMaterialTypeId());
    this.put("loanId", loan.getId());
    this.put("userId", loan.getUserId());
    this.put("itemId", item.getItemId());

    JsonObject paymentStatus = new JsonObject();
    JsonPropertyWriter.write(paymentStatus, "name", "Outstanding");
    this.put("paymentStatus", paymentStatus);

    JsonObject status = new JsonObject();
    JsonPropertyWriter.write(status, "name", "Open");
    this.put("status", status);
  }
}
