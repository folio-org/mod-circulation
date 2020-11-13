package org.folio.circulation.services.support;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountCancelReason;
import org.folio.circulation.domain.AccountRefundReason;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public final class RefundAndCancelAccountCommand {
  private final Account account;
  private final String staffUserId;
  private final String servicePointId;
  private final AccountRefundReason refundReason;
  private final AccountCancelReason cancelReason;
}
