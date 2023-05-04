package org.folio.circulation.domain.subscribers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getBigDecimalProperty;
import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class FeeFineBalanceChangedEvent {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private String id;
  private String feeFineId;
  private String userId;
  private String feeFineTypeId;
  private String loanId;
  private BigDecimal balance;

  public static FeeFineBalanceChangedEvent fromJson(JsonObject json) {
    log.debug("fromJson:: parameters json: {}", json);

    var result = new FeeFineBalanceChangedEvent(
      getProperty(json, "id"),
      getProperty(json, "feeFineId"),
      getProperty(json, "userId"),
      getProperty(json, "feeFineTypeId"),
      getProperty(json, "loanId"),
      getBigDecimalProperty(json, "balance"));

    log.info("fromJson:: result {}", result);
    return result;
  }
}
