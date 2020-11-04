package org.folio.circulation.services.feefine;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountRefundReason;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class AccountRefundCommand {
  private final Account account;
  private final String currentServicePointId;
  private final String userName;
  private final AccountRefundReason refundReason;

  public boolean hasPaidAndTransferredAmount() {
    return account.hasPaidOrTransferredAmount();
  }

  public String getAccountId() {
    return account.getId();
  }
}
