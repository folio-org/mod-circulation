package org.folio.circulation.domain;

import java.util.Optional;

import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class ClaimItemReturnedRequest extends ChangeItemStatusRequest {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";
  private final DateTime itemClaimedReturnedDateTime;

  public ClaimItemReturnedRequest(String loanId, DateTime dateTime, String comment) {
    super(loanId, comment);
    this.itemClaimedReturnedDateTime = dateTime;
  }

  public DateTime getItemClaimedReturnedDateTime() {
    return itemClaimedReturnedDateTime;
  }

  public static ClaimItemReturnedRequest from(String loanId, JsonObject body) {
    final ChangeItemStatusRequest changeStatusRequest = ChangeItemStatusRequest.from(loanId, body);

    final DateTime itemClaimedReturnedDate = Optional
      .ofNullable(body.getString(ITEM_CLAIMED_RETURNED_DATE))
      .map(DateTime::parse)
      .orElse(null);

    return new ClaimItemReturnedRequest(loanId, itemClaimedReturnedDate,
      changeStatusRequest.getComment());
  }
}
