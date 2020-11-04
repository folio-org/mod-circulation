package org.folio.circulation.services.feefine;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.AccountCancelReason;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class AccountCancelCommand {
  private final Account account;
  private final String currentServicePointId;
  private final String userName;
  private final AccountCancelReason cancellationReason;

  public boolean hasPaidOrTransferredAmount() {
    return account.hasPaidOrTransferredAmount();
  }

  public String getAccountId() {
    return account.getId();
  }
}
