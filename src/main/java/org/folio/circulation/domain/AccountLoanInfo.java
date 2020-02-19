package org.folio.circulation.domain;

public class AccountLoanInfo {
  private final String loanId;
  private final String userId;

  public AccountLoanInfo(String loanId, String userId) {
    this.loanId = loanId;
    this.userId = userId;
  }

  public String getLoanId() {
    return loanId;
  }

  public String getUserId() {
    return userId;
  }
}
