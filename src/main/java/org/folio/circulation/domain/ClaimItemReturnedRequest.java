package org.folio.circulation.domain;

import java.time.ZonedDateTime;

import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.folio.circulation.support.json.JsonPropertyFetcher;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(callSuper = true)
public class ClaimItemReturnedRequest extends ChangeItemStatusRequest {
  public static final String ITEM_CLAIMED_RETURNED_DATE = "itemClaimedReturnedDateTime";
  private final ZonedDateTime itemClaimedReturnedDateTime;

  public ClaimItemReturnedRequest(String loanId, ZonedDateTime dateTime, String comment) {
    super(loanId, comment);
    this.itemClaimedReturnedDateTime = dateTime;
  }

  public static ClaimItemReturnedRequest from(String loanId, JsonObject body) {
    final ChangeItemStatusRequest changeStatusRequest = ChangeItemStatusRequest.from(loanId, body);

    final ZonedDateTime itemClaimedReturnedDate = JsonPropertyFetcher
      .getDateTimeProperty(body, ITEM_CLAIMED_RETURNED_DATE);

    return new ClaimItemReturnedRequest(loanId, itemClaimedReturnedDate,
      changeStatusRequest.getComment());
  }
}
