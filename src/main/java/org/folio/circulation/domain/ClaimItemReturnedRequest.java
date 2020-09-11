package org.folio.circulation.domain;

import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.support.json.JsonPropertyFetcher;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;
import lombok.Getter;

@Getter
public class ClaimItemReturnedRequest extends ChangeItemStatusRequest {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";
  private final DateTime itemClaimedReturnedDateTime;

  public ClaimItemReturnedRequest(String loanId, DateTime dateTime, String comment) {
    super(loanId, comment);
    this.itemClaimedReturnedDateTime = dateTime;
  }

  public static ClaimItemReturnedRequest from(String loanId, JsonObject body) {
    final ChangeItemStatusRequest changeStatusRequest = ChangeItemStatusRequest.from(loanId, body);

    final DateTime itemClaimedReturnedDate = JsonPropertyFetcher
      .getDateTimeProperty(body, ITEM_CLAIMED_RETURNED_DATE);

    return new ClaimItemReturnedRequest(loanId, itemClaimedReturnedDate,
      changeStatusRequest.getComment());
  }
}
