package org.folio.circulation.domain;

import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class ClaimItemReturnedRequest {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";
  public static final String COMMENT = "comment";

  private final String loanId;
  private final String comment;
  private final DateTime itemClaimedReturnedDateTime;

  private ClaimItemReturnedRequest(String loanId, DateTime dateTime, String comment) {

    this.loanId = loanId;
    this.comment = comment;
    this.itemClaimedReturnedDateTime = dateTime;
  }

  public String getLoanId() {
    return loanId;
  }

  public String getComment() {
    return comment;
  }

  public DateTime getItemClaimedReturnedDateTime() {
    return itemClaimedReturnedDateTime;
  }

  public static ClaimItemReturnedRequest from(RoutingContext routingContext) {
    final String loanId = routingContext.pathParam("id");
    final JsonObject body = routingContext.getBodyAsJson();

    return new ClaimItemReturnedRequest(
      loanId, DateTime.parse(body.getString(ITEM_CLAIMED_RETURNED_DATE)),
      body.getString(COMMENT));
  }
}
