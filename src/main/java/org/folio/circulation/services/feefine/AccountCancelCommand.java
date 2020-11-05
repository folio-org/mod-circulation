package org.folio.circulation.services.feefine;

import org.folio.circulation.domain.AccountCancelReason;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public final class AccountCancelCommand {
  private final String accountId;
  private final String currentServicePointId;
  private final String userName;
  private final AccountCancelReason cancellationReason;
}
