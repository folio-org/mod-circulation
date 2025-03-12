package org.folio.circulation.domain;

import java.time.ZonedDateTime;

public class AccountLoanInfo {
  private final String loanId;
  private final String userId;

  private final ZonedDateTime dueDate;

  public AccountLoanInfo(String loanId, String userId, ZonedDateTime dueDate) {
    this.loanId = loanId;
    this.userId = userId;
    this.dueDate = dueDate;

  }

  public String getLoanId() {
    return loanId;
  }

  public String getUserId() {
    return userId;
  }

  public ZonedDateTime getDueDate() {
    return dueDate;
  }

}
