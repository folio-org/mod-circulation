package org.folio.circulation.domain.loan;

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
      .put("dateTime", dateTime.toString())
      .put("staffMemberId", staffMemberId);
  }
}
