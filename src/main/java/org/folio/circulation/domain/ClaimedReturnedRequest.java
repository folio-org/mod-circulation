package org.folio.circulation.domain;

import org.folio.circulation.domain.loan.LoanClaimedReturned;
import org.folio.circulation.support.http.server.WebContext;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ClaimedReturnedRequest {
  private final String loanId;
  private final String comment;
  private final LoanClaimedReturned loanClaimedReturned;

  private ClaimedReturnedRequest(String loanId, DateTime dateTime, String comment,
    String staffMemberId) {

    this.loanId = loanId;
    this.comment = comment;
    this.loanClaimedReturned = new LoanClaimedReturned(dateTime, staffMemberId);
  }

  public String getLoanId() {
    return loanId;
  }

  public String getComment() {
    return comment;
  }

  public LoanClaimedReturned getLoanClaimedReturned() {
    return loanClaimedReturned;
  }

  public static ClaimedReturnedRequest from(WebContext webContext) {
    final RoutingContext routingContext = webContext.getRoutingContext();

    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    return new ClaimedReturnedRequest(
      loanId,
      DateTime.parse(body.getString("dateTime")),
      body.getString("comment"),
      webContext.getUserId());
  }
}
