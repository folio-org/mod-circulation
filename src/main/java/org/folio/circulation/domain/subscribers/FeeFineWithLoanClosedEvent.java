package org.folio.circulation.domain.subscribers;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class FeeFineWithLoanClosedEvent {
  private final String loanId;

  public FeeFineWithLoanClosedEvent(String loanId) {
    this.loanId = loanId;
  }

  public String getLoanId() {
    return loanId;
  }

  public static FeeFineWithLoanClosedEvent fromJson(JsonObject json) {
    return new FeeFineWithLoanClosedEvent(getProperty(json, "loanId"));
  }
}
