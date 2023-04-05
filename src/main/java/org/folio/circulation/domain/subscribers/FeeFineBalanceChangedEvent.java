package org.folio.circulation.domain.subscribers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.math.BigDecimal;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FeeFineBalanceChangedEvent {
  private String id;
  private String feeFineId;
  private String userId;
  private String feeFineTypeId;
  private String loanId;
  private BigDecimal balance;

  public static FeeFineBalanceChangedEvent fromJson(JsonObject json) {
    return new FeeFineBalanceChangedEvent(
      getProperty(json, "id"),
      getProperty(json, "feeFineId"),
      getProperty(json, "userId"),
      getProperty(json, "feeFineTypeId"),
      getProperty(json, "loanId"),
      getBigDecimalProperty(json, "balance"));
  }
}
