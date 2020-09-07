package org.folio.circulation.domain.representations;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChangeItemStatusRequest {
  private final String loanId;
  private final String comment;

  public static ChangeItemStatusRequest from(String loanId, JsonObject body) {
    return new ChangeItemStatusRequest(loanId, body.getString("comment"));
  }
}
