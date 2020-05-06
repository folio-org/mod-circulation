package org.folio.circulation.domain;

import static org.folio.circulation.support.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

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

  public BigDecimal getAmountAction() {
    return getBigDecimalProperty(representation, "amountAction");
  }

  public String getUserId() {
    return representation.getString("userId");
  }

  public String getTypeAction() {
    return representation.getString("typeAction");
  }

  public boolean isPaid() {
    return isTypeActionStartsWith("Paid");
  }

  public boolean isTransferred() {
    return isTypeActionStartsWith("Transferred");
  }

  private boolean isTypeActionStartsWith(String prefix) {
    return getTypeAction() != null && getTypeAction().toLowerCase()
      .startsWith(prefix.toLowerCase());
  }

  public DateTime getDateAction() {
    return getDateTimeProperty(representation, "dateAction");
  }

  public String getComments() {
    return representation.getString("comments");
  }

  public String getId() {
    return representation.getString("id");
  }

  public static FeeFineAction from(JsonObject representation) {
    return new FeeFineAction(representation);
  }
}
