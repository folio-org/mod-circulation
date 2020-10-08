package org.folio.circulation.services.support;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.representations.AccountPaymentStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class RefundAccountCommand {
  private final Account accountToRefund;
  private final String staffUserId;
  private final String servicePointId;
  private final AccountPaymentStatus cancellationReason;
}
