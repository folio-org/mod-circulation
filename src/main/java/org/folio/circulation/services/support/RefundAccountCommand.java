package org.folio.circulation.services.support;

import org.folio.circulation.domain.Account;

public final class RefundAccountCommand {
  private final Account accountToRefund;
  private final String staffUserId;
  private final String servicePointId;

  public RefundAccountCommand(Account accountToRefund, String userId, String servicePointId) {
    this.accountToRefund = accountToRefund;
    this.staffUserId = userId;
    this.servicePointId = servicePointId;
  }

  public Account getAccountToRefund() {
    return accountToRefund;
  }

  public String getStaffUserId() {
    return staffUserId;
  }

  public String getServicePointId() {
    return servicePointId;
  }
}
