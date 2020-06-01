package org.folio.circulation.domain.subscribers;

import static org.folio.circulation.support.JsonPropertyFetcher.getProperty;

import io.vertx.core.json.JsonObject;

public class LoanRelatedFeeFineClosedEvent {
  private final String loanId;

  public LoanRelatedFeeFineClosedEvent(String loanId) {
    this.loanId = loanId;
  }

  public String getLoanId() {
    return loanId;
  }

  public static LoanRelatedFeeFineClosedEvent fromJson(JsonObject json) {
    return new LoanRelatedFeeFineClosedEvent(getProperty(json, "loanId"));
  }
}
