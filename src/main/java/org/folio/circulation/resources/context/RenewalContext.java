package org.folio.circulation.resources.context;

import java.time.ZoneId;

import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;

import io.vertx.core.json.JsonObject;
import lombok.ToString;
import lombok.Value;
import lombok.With;
import lombok.val;

@Value
@With
@ToString(onlyExplicitlyIncluded = true)
public class RenewalContext {
  @ToString.Include
  Loan loan;
  RequestQueue requestQueue;
  ZoneId timeZone;
  Loan loanBeforeRenewal;
  ItemStatus itemStatusBeforeRenewal;
  String loggedInUserId;
  JsonObject renewalRequest;
  FeeFineAction overdueFeeFineAction;
  TlrSettingsConfiguration tlrSettings;

  public static RenewalContext create(Loan loan, JsonObject renewalRequest,
    String loggedInUserId) {

    val loanBeforeRenewal = loan != null ? loan.copy() : null;
    val itemStatusBeforeRenew = loan != null && loan.getItem() != null
      ? loan.getItem().getStatus() : null;

    return new RenewalContext(loan, null, null, loanBeforeRenewal, itemStatusBeforeRenew,
      loggedInUserId, renewalRequest, null, null);
  }
}
