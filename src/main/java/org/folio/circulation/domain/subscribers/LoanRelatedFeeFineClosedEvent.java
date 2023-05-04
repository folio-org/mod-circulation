package org.folio.circulation.domain.subscribers;

import static org.folio.circulation.support.json.JsonPropertyFetcher.getProperty;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.ToString;

@ToString
public class LoanRelatedFeeFineClosedEvent {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String loanId;

  public LoanRelatedFeeFineClosedEvent(String loanId) {
    this.loanId = loanId;
  }

  public String getLoanId() {
    return loanId;
  }

  public static LoanRelatedFeeFineClosedEvent fromJson(JsonObject json) {
    log.debug("fromJson:: parameters json: {}", json);
    var result = new LoanRelatedFeeFineClosedEvent(getProperty(json, "loanId"));
    log.info("fromJson:: result {}", result);
    return result;
  }
}
