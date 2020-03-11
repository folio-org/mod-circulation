package org.folio.circulation.domain.representations;

import java.util.UUID;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.FeeFine;
import org.folio.circulation.domain.FeeFineOwner;
import org.folio.circulation.domain.User;
import org.folio.circulation.support.ClockManager;
import org.folio.circulation.support.JsonPropertyWriter;

import io.vertx.core.json.JsonObject;

public class FeeFineActionStorageRepresentation extends JsonObject {
  public FeeFineActionStorageRepresentation(Account account, FeeFineOwner feeFineOwner,
    FeeFine feeFine, Double amount, Double balance, User loggedInUser) {
    super();

    if (account == null || feeFine == null || feeFineOwner == null) {
      return;
    }

    this.put("id", UUID.randomUUID().toString());
    this.put("userId", account.getUserId());
    this.put("accountId", account.getId());
    this.put("source", loggedInUser == null ? "" : String.format("%s, %s",
      loggedInUser.getLastName(), loggedInUser.getFirstName()));
    this.put("createdAt", feeFineOwner.getOwner());
    this.put("transactionInformation", "-");
    this.put("balance", balance);
    this.put("amountAction", amount);
    this.put("notify", false);
    this.put("typeAction", feeFine.getFeeFineType());
    JsonPropertyWriter.write(this, "dateAction", ClockManager.getClockManager().getDateTime());
  }
}
