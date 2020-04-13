package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;

public class ChangeItemStatusRequest {
  public static final String COMMENT = "comment";
  private final String loanId;
  private final String comment;

  public ChangeItemStatusRequest(String loanId, String comment) {
    this.comment = comment;
    this.loanId = loanId;
  }

  public String getComment() {
    return comment;
  }

  public String getLoanId() {
    return loanId;
  }

  public static ChangeItemStatusRequest from(String loanId, JsonObject body) {
    return new ChangeItemStatusRequest(loanId, body.getString(COMMENT));
  }
}
