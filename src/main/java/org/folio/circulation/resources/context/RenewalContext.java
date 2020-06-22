package org.folio.circulation.resources.context;

import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.joda.time.DateTimeZone;

import io.vertx.core.json.JsonObject;
import lombok.Value;
import lombok.With;
import lombok.val;

@Value
@With
public class RenewalContext {
  Loan loan;
  RequestQueue requestQueue;
  DateTimeZone timeZone;
  Loan loanBeforeRenewal;
  ItemStatus itemStatusBeforeRenewal;
  boolean lostItemFeesRefundedOrCancelled;
  String loggedInUserId;
  JsonObject renewalRequest;

  public static RenewalContext create(Loan loan, JsonObject renewalRequest,
    String loggedInUserId) {

    val loanBeforeRenewal = loan != null ? loan.copy() : null;
    val itemStatusBeforeRenew = loan != null && loan.getItem() != null
      ? loan.getItem().getStatus() : null;

    return new RenewalContext(loan, null, null, loanBeforeRenewal, itemStatusBeforeRenew,
      false, loggedInUserId, renewalRequest);
  }
}
