package org.folio.circulation.resources.context;

import java.time.ZoneId;
import java.util.UUID;

import org.folio.circulation.domain.FeeFineAction;
import org.folio.circulation.domain.ItemStatus;
import org.folio.circulation.domain.Loan;
import org.folio.circulation.domain.RequestQueue;
import org.folio.circulation.domain.configuration.TlrSettingsConfiguration;

import io.vertx.core.json.JsonObject;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.With;

@Value
@EqualsAndHashCode
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
  @EqualsAndHashCode.Exclude
  String performanceAnalysisId;
  @EqualsAndHashCode.Exclude
  Long lastPerformanceTimestampMillis;
  FeeFineAction overdueFeeFineAction;
  TlrSettingsConfiguration tlrSettings;

  public static RenewalContext create(Loan loan, JsonObject renewalRequest,
    String loggedInUserId) {
    return create(loan, renewalRequest, loggedInUserId, UUID.randomUUID().toString(),
      System.currentTimeMillis());
  }

  public static RenewalContext create(Loan loan, JsonObject renewalRequest,
    String loggedInUserId, String performanceAnalysisId,
    long lastPerformanceTimestampMillis) {
    final Loan loanBeforeRenewal = loan != null ? loan.copy() : null;
    final ItemStatus itemStatusBeforeRenew = loan != null && loan.getItem() != null
      ? loan.getItem().getStatus() : null;

    return new RenewalContext(loan, null, null, loanBeforeRenewal, itemStatusBeforeRenew,
      loggedInUserId, renewalRequest, performanceAnalysisId,
      lastPerformanceTimestampMillis, null, null);
  }
}
