package org.folio.circulation.services.support;

import org.folio.circulation.domain.Account;
import org.folio.circulation.domain.representations.FeeFinePaymentAction;

public final class AccountCancellation {
  private final Account accountToCancel;
  private final FeeFinePaymentAction cancellationReason;
  private final String staffUserId;
  private final String servicePointId;

  private AccountCancellation(AccountCancellationBuilder builder) {
    accountToCancel = builder.accountToCancel;
    cancellationReason = builder.cancellationReason;
    staffUserId = builder.staffUserId;
    servicePointId = builder.servicePointId;
  }

  public static AccountCancellationBuilder builder() {
    return new AccountCancellationBuilder();
  }

  public Account getAccountToCancel() {
    return accountToCancel;
  }

  public FeeFinePaymentAction getCancellationReason() {
    return cancellationReason;
  }

  public String getStaffUserId() {
    return staffUserId;
  }

  public String getServicePointId() {
    return servicePointId;
  }

  public static final class AccountCancellationBuilder {
    private Account accountToCancel;
    private FeeFinePaymentAction cancellationReason;
    private String staffUserId;
    private String servicePointId;

    private AccountCancellationBuilder() {}

    public AccountCancellationBuilder withAccountToCancel(Account accountToCancel) {
      this.accountToCancel = accountToCancel;
      return this;
    }

    public AccountCancellationBuilder withCancellationReason(FeeFinePaymentAction cancellationReason) {
      this.cancellationReason = cancellationReason;
      return this;
    }

    public AccountCancellationBuilder withStaffUserId(String staffUserId) {
      this.staffUserId = staffUserId;
      return this;
    }

    public AccountCancellationBuilder withServicePointId(String servicePointId) {
      this.servicePointId = servicePointId;
      return this;
    }

    public AccountCancellation build() {
      return new AccountCancellation(this);
    }
  }
}
