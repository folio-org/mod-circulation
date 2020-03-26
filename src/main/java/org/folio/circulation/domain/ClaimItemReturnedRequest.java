package org.folio.circulation.domain;

import org.folio.circulation.domain.representations.ChangeItemStatusRequest;
import org.joda.time.DateTime;

public class ClaimItemReturnedRequest extends ChangeItemStatusRequest {
  private final DateTime itemClaimedReturnedDateTime;

  public ClaimItemReturnedRequest(String loanId, DateTime dateTime, String comment) {
    super(loanId, comment);
    this.itemClaimedReturnedDateTime = dateTime;
  }

  public DateTime getItemClaimedReturnedDateTime() {
    return itemClaimedReturnedDateTime;
  }
}
