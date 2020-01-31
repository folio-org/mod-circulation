package org.folio.circulation.domain.loan;

import org.folio.circulation.domain.representations.LoanProperties;
import org.joda.time.DateTime;

import io.vertx.core.json.JsonObject;

public class LoanClaimedReturned {
  private final DateTime dateTime;
  private final String staffMemberId;

  public LoanClaimedReturned(DateTime dateTime, String staffMemberId) {
    this.dateTime = dateTime;
    this.staffMemberId = staffMemberId;
  }

  public DateTime getDateTime() {
    return dateTime;
  }

  public JsonObject toJson() {
    return new JsonObject()
      .put(LoanProperties.ClaimedReturned.DATE_TIME, dateTime.toString())
      .put(LoanProperties.ClaimedReturned.STAFF_MEMBER_ID, staffMemberId);
  }
}
