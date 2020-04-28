package org.folio.circulation.domain.representations;

import org.joda.time.DateTime;

public class ChangeDueDateRequest {
  public static final String DUE_DATE = "dueDate";

  private final String loanId;
  private final DateTime dueDate;

  public ChangeDueDateRequest(String loanId, DateTime dueDate) {
    this.loanId = loanId;
    this.dueDate = dueDate;
  }

  public String getLoanId() {
    return loanId;
  }

  public DateTime getDueDate() {
    return dueDate;
  }
}
