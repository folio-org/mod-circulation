package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getNestedStringProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class Account {

  private final JsonObject representation;

  public Account(JsonObject representation) {
    this.representation = representation;
  }

  public String getId() {
    return getProperty(representation, "id");
  }

  public String getLoanId() {
    return getProperty(representation, "loanId");
  }

  public Double getRemainingFeeFineAmount() {
    return representation.getDouble("remaining");
  }

  public String getStatus() {
    return getNestedStringProperty(representation, "status", "name");
  }

  public boolean isClosed() {
    return getStatus().equalsIgnoreCase("closed");
  }

  public boolean isOpen() {
    return getStatus().equalsIgnoreCase("open");
  }

  public static Account from(JsonObject representation) {
    return new Account(representation);
  }
}
