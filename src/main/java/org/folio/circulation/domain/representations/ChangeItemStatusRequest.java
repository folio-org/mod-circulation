package org.folio.circulation.domain.representations;

import java.lang.invoke.MethodHandles;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@AllArgsConstructor
@ToString
public class ChangeItemStatusRequest {
  private static final Logger log = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final String loanId;
  private final String comment;

  public static ChangeItemStatusRequest from(String loanId, JsonObject body) {
    log.debug("from:: parameters loanId: {}, body: {}", loanId, body);

    return new ChangeItemStatusRequest(loanId, body.getString("comment"));
  }
}
