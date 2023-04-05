package org.folio.circulation.domain;

import static java.lang.String.format;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getDateTimeProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.time.ZonedDateTime;

import io.vertx.core.json.JsonObject;

public class FeeFineAction {
  private final JsonObject representation;

  public FeeFineAction(JsonObject representation) {
    this.representation = representation;
  }

  public String getAccountId() {
    return getProperty(representation, "accountId");
  }

  public FeeAmount getBalance() {
    return FeeAmount.from(representation,"balance");
  }

  public FeeAmount getAmount() {
    return FeeAmount.from(representation, "amountAction");
  }

  public String getUserId() {
    return representation.getString("userId");
  }

  public String getActionType() {
    return representation.getString("typeAction");
  }

  public boolean isPaid() {
    return isActionTypeStartsWith("Paid");
  }

  public boolean isTransferred() {
    return isActionTypeStartsWith("Transferred");
  }

  public boolean isCredited() {
    return isActionTypeStartsWith("Credited");
  }

  private boolean isActionTypeStartsWith(String prefix) {
    return getActionType() != null && getActionType().toLowerCase()
      .startsWith(prefix.toLowerCase());
  }

  public ZonedDateTime getDateAction() {
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

  public String getPaymentMethod() {
    return representation.getString("paymentMethod");
  }

  @Override
  public String toString() {
    return format("FeeFineAction(id=%s, accountId=%s, typeAction=%s, amount=%s)", getId(),
      getAccountId(), getActionType(), getAmount().toScaledString());
  }
}
