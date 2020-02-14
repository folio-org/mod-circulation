package org.folio.circulation.domain;

public class AccountRelatedRecordsInfo {
  private final AccountFeeFineOwnerInfo feeFineOwnerInfo;
  private final AccountFeeFineTypeInfo feeFineTypeInfo;
  private final AccountLoanInfo loanInfo;
  private final AccountItemInfo itemInfo;

  public AccountRelatedRecordsInfo(AccountFeeFineOwnerInfo feeFineOwnerInfo,
    AccountFeeFineTypeInfo feeFineTypeInfo, AccountLoanInfo loanInfo, AccountItemInfo itemInfo) {
    this.feeFineOwnerInfo = feeFineOwnerInfo;
    this.feeFineTypeInfo = feeFineTypeInfo;
    this.loanInfo = loanInfo;
    this.itemInfo = itemInfo;
  }

  public AccountFeeFineOwnerInfo getFeeFineOwnerInfo() {
    return feeFineOwnerInfo;
  }

  public AccountFeeFineTypeInfo getFeeFineTypeInfo() {
    return feeFineTypeInfo;
  }

  public AccountLoanInfo getLoanInfo() {
    return loanInfo;
  }

  public AccountItemInfo getItemInfo() {
    return itemInfo;
  }
}
