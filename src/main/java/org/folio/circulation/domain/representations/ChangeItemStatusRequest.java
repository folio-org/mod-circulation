package org.folio.circulation.domain.representations;

public class ChangeItemStatusRequest {
  private final String loanId;
  private final String comment;

  public ChangeItemStatusRequest(String loanId, String comment) {
    this.comment = comment;
    this.loanId = loanId;
  }

  public String getComment() {
    return comment;
  }

  public String getLoanId() {
    return loanId;
  }
}
