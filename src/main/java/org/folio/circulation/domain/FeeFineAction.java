package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class FeeFineAction {
  private final JsonObject representation;

  public FeeFineAction(JsonObject representation) {
    this.representation = representation;
  }

  public String getAccountId() {
    return getProperty(representation, "accountId");
  }

  public Double getBalance() {
    return representation.getDouble("balance");
  }

  public DateTime getDateAction() {
    return getDateTimeProperty(representation, "dateAction");
  }

  public static FeeFineAction from(JsonObject representation) {
    return new FeeFineAction(representation);
  }
}
