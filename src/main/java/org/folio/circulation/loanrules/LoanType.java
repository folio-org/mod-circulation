package org.folio.circulation.loanrules;

public class LoanType {
  public String id;
  public LoanType(String id) {
    this.id = id;
  }
  @Override
  public String toString() {
    return id;
  }
}
