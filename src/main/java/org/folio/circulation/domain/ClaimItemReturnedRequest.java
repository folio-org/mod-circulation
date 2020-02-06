package org.folio.circulation.domain;

import org.joda.time.DateTime;

public class ClaimItemReturnedRequest {
  private final String loanId;
  private final String comment;
  private final DateTime itemClaimedReturnedDateTime;

  public ClaimItemReturnedRequest(String loanId, DateTime dateTime, String comment) {
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
}
